package SandBox;

/** 
 * Albert Cardona, Ignacio Arganda-Carreras and Stephan Saalfeld. 
 * This work released under the terms of the General Public License in its latest edition. 
 * */

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.plugin.PlugIn;
import ij.VirtualStack;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.io.FileSaver;
import ij.io.OpenDialog;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;



import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;

import javax.swing.JFileChooser;



/** 
 * Fiji plugin to register sequences of images in a concurrent (multi-thread) way.
 * <p>
 * <b>Requires</b>: a directory with images, of any size and type (8, 16, 32-bit gray-scale or RGB color)
 * <p>
 * <b>Performs</b>: registration of a sequence of images, by 6 different registration models:
 * <ul>
 * 				<li> Translation (no deformation)</li>
 * 				<li> Rigid (translation + rotation)</li>
 * 				<li> Similarity (translation + rotation + isotropic scaling)</li>
 * 				<li> Affine (free affine transformation)</li>
 * 				<li> Elastic (consistent elastic deformations by B-splines)</li>
 * 				<li> Moving least squares (maximal warping)</li>
 * </ul>
 * <p>
 * <b>Outputs</b>: the list of new images, one for slice, into a target directory as .tif files.
 * <p>
 * For a detailed documentation, please visit the plugin website at:
 * <p>
 * <A target="_blank" href="http://fiji.sc/wiki/Register_Virtual_Stack_Slices">http://fiji.sc/wiki/Register_Virtual_Stack_Slices</A>
 * 
 * @author Ignacio Arganda-Carreras (ignacio.arganda@gmail.com), Stephan Saalfeld and Albert Cardona
 */
public class CheckBoxDemo implements PlugIn 
{

	// Registration types
	/** translation registration model id */
	public static final int TRANSLATION 			= 0;
	/** rigid-body registration model id */
	public static final int RIGID 					= 1;
	/** rigid-body + isotropic scaling registration model id */
	public static final int SIMILARITY 				= 2;
	/** affine registration model id */
	public static final int AFFINE 					= 3;
	/** elastic registration model id */
	public static final int ELASTIC 				= 4;
	/** maximal warping registration model id */
	public static final int MOVING_LEAST_SQUARES 	= 5;
	
	/** index of the features model check-box */
	public static int featuresModelIndex = CheckBoxDemo.RIGID;
	/** index of the registration model check-box */
	public static int registrationModelIndex = CheckBoxDemo.RIGID;
	/** working directory path */
	public static String currentDirectory = (OpenDialog.getLastDirectory() == null) ? 
					 OpenDialog.getDefaultDirectory() : OpenDialog.getLastDirectory();
					 
	/** advance options flag */
	public static boolean advanced = false;
	/** shrinkage constraint flag */
	public static boolean non_shrinkage = false;
	/** save transformation flag */
	public static boolean save_transforms = false;
	
	/** source directory **/
	public static String sourceDirectory="";
	/** output directory **/
	public static String outputDirectory="";
	
	// Regularization 
	/** scaling regularization parameter [0.0-1.0] */
	public static double tweakScale = 0.95;
	/** shear regularization parameter [0.0-1.0] */
	public static double tweakShear = 0.95;
	/** isotropy (aspect ratio) regularization parameter [0.0-1.0] */
	public static double tweakIso = 0.95;
	
	/** display relaxation graph flag */
	public static boolean displayRelaxGraph = false;
	
	// Image centers
	/** array of x- coordinate image centers */ 
	private static double[] centerX = null;
	/** array of y- coordinate image centers */
	private static double[] centerY = null;
	
	/** post-processing flag */
	public static boolean postprocess = true;
	/** debug flat to print out intermediate results and information */
	private static boolean debug = false;

	/** registration model string labels */
	public static final String[] registrationModelStrings =
			       {"Translation          -- no deformation                      ",
	  	            "Rigid                -- translate + rotate                  ",
			        "Similarity           -- translate + rotate + isotropic scale",
			        "Affine               -- free affine transform               ",
			        "Elastic              -- bUnwarpJ splines                    ",
			        "Moving least squares -- maximal warping                     "};
	

	/** feature model string labels */
	public final static String[] featuresModelStrings = new String[]{ "Translation", "Rigid", "Similarity", "Affine" };
	/** relaxation threshold (if the difference between last two iterations is below this threshold, the relaxation stops */
	public static final float STOP_THRESHOLD = 0.01f;
	/** maximum number of iterations in the relaxation loop */
	public static final int MAX_ITER = 300;


	//---------------------------------------------------------------------------------
	/**
	 * Plug-in run method
	 * 
	 * @param arg plug-in arguments
	 */
	public void run(String arg) 
	{
		GenericDialogPlus gd = new GenericDialogPlus("Register Virtual Stack");

		gd.addDirectoryField("Source directory", sourceDirectory, 50);
		gd.addDirectoryField("Output directory", outputDirectory, 50);
		gd.addChoice("Feature extraction model: ", featuresModelStrings, featuresModelStrings[featuresModelIndex]);
		gd.addChoice("Registration model: ", registrationModelStrings, registrationModelStrings[registrationModelIndex]);
		gd.addCheckbox("Advanced setup", advanced);	
		gd.addCheckbox("Shrinkage constrain", non_shrinkage);
		gd.addCheckbox("Save transforms", save_transforms);
		
		gd.showDialog();
		
		// Exit when canceled
		if (gd.wasCanceled()) 
			return;
				
		sourceDirectory = gd.getNextString();
		outputDirectory = gd.getNextString();
		featuresModelIndex = gd.getNextChoiceIndex();
		registrationModelIndex = gd.getNextChoiceIndex();
		advanced = gd.getNextBoolean();
		non_shrinkage = gd.getNextBoolean();
		save_transforms = gd.getNextBoolean();

		String source_dir = sourceDirectory;
		if (null == source_dir) 
		{
			IJ.error("Error: No source directory was provided.");
			return;
		}
		
		// Check if source directory exists
		if( (new File( source_dir )).exists() == false )
		{
			IJ.error("Error: source directory " + source_dir + " does not exist.");
			return;
		}
		
		source_dir = source_dir.replace('\\', '/');
		if (!source_dir.endsWith("/")) source_dir += "/";
		
		String target_dir = outputDirectory;
		if (null == target_dir) 
		{
			IJ.error("Error: No output directory was provided.");
			return;
		}
		
		// Check if output directory exists
		if( (new File( target_dir )).exists() == false )
		{
			IJ.error("Error: output directory " + target_dir + " does not exist.");
			return;
		}
		
		target_dir = target_dir.replace('\\', '/');
		if (!target_dir.endsWith("/")) target_dir += "/";
		
		// Select folder to save the transformation files if
		// the "Save transforms" check-box was checked.
		String save_dir = null;
		if(save_transforms)
		{
			// Choose target folder to save images into
			JFileChooser chooser = new JFileChooser(source_dir); 			
			chooser.setDialogTitle("Choose directory to store Transform files");
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setAcceptAllFileFilterUsed(true);
			if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
		    	return;
			
			save_dir = chooser.getSelectedFile().toString();
			if (null == save_dir) 
				return;
			save_dir = save_dir.replace('\\', '/');
			if (!save_dir.endsWith("/")) save_dir += "/";
		}
		
		// Select reference
		String referenceName = null;						
		if(non_shrinkage == false)
		{		
			JFileChooser chooser = new JFileChooser(source_dir); 
			// Choose reference image
			chooser.setDialogTitle("Choose reference image");
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			chooser.setAcceptAllFileFilterUsed(true);
			if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
				return;
			referenceName = chooser.getSelectedFile().getName();
		}


		// Execute registration
		
	}
	
	

					e.printStackTrace();					
					return null;
				}
			}
		};
	} // end saveTransform method
	//-----------------------------------------------------------------------------------------
	/**
	
}// end Register_Virtual_Stack_MT class