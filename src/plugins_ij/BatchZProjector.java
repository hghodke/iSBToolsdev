package plugins_ij;

/** 
 * Victor Caldas
 * This work released under the terms of the General Public License in its latest edition. 
 * */

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.swing.JOptionPane;

/** 
 *  Descrive the plugin
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
 * @author Victor E. A. Caldas (caldas.victor@gmail.com)
 */

public class BatchZProjector implements PlugIn{
	
	/** Defining Variables **/
	/** Input files and folders */
	static String[] labels;
	static String[][] CSVContent;
	static ResultsTable table;
	static String ImagesFolder;
	
	
	
	//sync test
	
	
	
	
	public static void main(String[] args) throws IOException{
		new BatchZProjector().run("");
		System.out.println("---------Done!-------------");
		java.awt.Toolkit.getDefaultToolkit().beep();
	}
	
	
	public void run(String arg0){
		
		//Get file
		String csvFilename =  IJ.getFilePath("Provide ControlFile.CSV");
		if (csvFilename==null) return;	
		File file = new File(csvFilename);
		//Load table
		
		ImagesFolder = tools.iSBOps.checkCreateDir(file.getParent()+File.separator+"Projections");

		loadTable(csvFilename);
		
		//Get Unique tags related to the possible channels
	  	List<String> uniques = getUniqueTags("Channel", table);
		decision_tree(uniques);
	 
		try {
			table.saveAs(csvFilename);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		IJ.log("Batch Z Prokection Created!!");
	}
	
	
	
	
	public static void decision_tree(List<String> list) {
		//check if collum existe
		//int TrimColIndex = table.getColumnIndex("Trimmer");
		//table.incrementCounter();
		//System.out.println(TrimColIndex);
		
		String[] items = list.toArray(new String[list.size()]);
		
		
		int choice = ConfirmExecution(items);
	  	
	  	  	
	  	if( choice == -1){
	  		System.out.println("Action cancelled");
	  	}
	  	
	  	
	  	
	  	
	  	else{
	  		System.out.println("Choice made: "+ items[choice]);
	  	
	  		//Ask threshold values
	  		String channel = items[choice];
	  		//Create the image to store get the first match
	  		
	  		
	  		ImagePlus averageImp = null;
	  		int StackSize = 0;
	  		
	  		for(int row=0; row<table.getCounter(); row++){
	  			String currentChannel = table.getStringValue("Channel", row);
	  			String pathToFile = table.getStringValue("WorkingFile", row);
	  			
	  			if(currentChannel.equalsIgnoreCase(channel)){
	  				StackSize++;
	  			}
	  		}
	  		System.out.println(StackSize);
	  		
	  		
	  		for(int row=0; row<table.getCounter(); row++){
	  			String currentChannel = table.getStringValue("Channel", row);
	  			String pathToFile = table.getStringValue("WorkingFile", row);
	  			
	  			if(currentChannel.equalsIgnoreCase(channel)){
	  				ImagePlus tempImp = IJ.openImage(pathToFile);
	  				averageImp = IJ.createImage(channel+"_avgStack", tempImp.getWidth(), tempImp.getHeight(), StackSize, 16);
	  				tempImp.close();
	  			}
	  		}
	  		
	  		
	  		
	  		
	  		
	  		
	  		ImageStack stack = averageImp.getStack();
	  		int currentStack = 1;
	  		
	  		for(int row=0; row<table.getCounter(); row++){
	  			
	  			String currentChannel = table.getStringValue("Channel", row);
	  			if(currentChannel.equalsIgnoreCase(items[choice])){
	  				System.out.println(StackSize);
	  				String pathToFile = table.getStringValue("WorkingFile", row);
	  				
	  				ImagePlus imp = IJ.openImage(pathToFile);
	  				String ImageName = imp.getShortTitle();
	  				
	  				
	  				
	  				
	  				//get Average Projection
	  				
	  				ZProjector projector = new ZProjector(imp);
	  				projector.setMethod(ZProjector.AVG_METHOD);
	  				projector.doProjection();
	  				
	  				
	  				
	  				//
	  				
	  				stack.setSliceLabel(ImageName, currentStack);
	  				stack.setProcessor(projector.getProjection().getProcessor(), currentStack);
	  					
	  				
	  				currentStack++;
	  			}
	  			
	  			
	  		}
	  		
	  		IJ.saveAsTiff(averageImp, ImagesFolder+ File.separator + "Projection_"+ items[choice]);
	  		
	 				
			int choice2 = askToContinue();	
	  		
	  		if(choice2 == 0){
	  			decision_tree(list);
	  		}
	  		else if (choice2 == 1){
	  			System.out.println("Action cancelled");
	  	 		}
	  		
	  		
		}
	}

	private static int ConfirmExecution(String[] labels) {
		
		
		Object[] options2 = labels;

	    Component frame2 = null;
	    int DKSelection= JOptionPane.showOptionDialog(frame2,
	    		"Batch ZProjector: ",
	    		"Choose Channel",
	    		JOptionPane.YES_NO_OPTION,
	    		JOptionPane.QUESTION_MESSAGE,
	    		null,     //do not use a custom Icon
	    		options2,  //the titles of buttons
	    		options2[0]); //default button title
	return DKSelection;// TODO Auto-generated method stub
		
	}
	

	



	private static int askToContinue() {
		Object[] options2 = {"Yes","No"};

	    Component frame2 = null;
	    int DKSelection= JOptionPane.showOptionDialog(frame2,
	    		"Do you wish to continue with another channel?: ",
	    		"Dark Count Correction",
	    		JOptionPane.YES_NO_OPTION,
	    		JOptionPane.QUESTION_MESSAGE,
	    		null,     //do not use a custom Icon
	    		options2,  //the titles of buttons
	    		options2[0]); //default button title
	return DKSelection;// TODO Auto-generated method stub
		

	}

	
	
	private void loadTable(String csvFilename) {
		try {
			table = ResultsTable.open(csvFilename);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}


	private List<String> getUniqueTags(String collum, ResultsTable table) {
		List<String> list = getAllTags(collum, table);
		HashSet<String> repeated = new HashSet<String>();
		repeated.addAll(list);
		list.clear();
		list.addAll(repeated);
		return list;
		
	}
	
	private List<String> getAllTags(String collum, ResultsTable table)
	{
		
		List<String> list = new ArrayList<>();
		
		
		for (int i=0; i<table.getCounter(); i++){
			String tag = table.getStringValue(collum, i);
			list.add(tag);
			}
			
				
		return list;
		
	}
	

		
}
