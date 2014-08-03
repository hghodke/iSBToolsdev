package SandBox;

import java.awt.*;

public class checkbox extends Panel {
 /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
public static int HORIZONTAL = 0;
 public static int VERTICAL = 1;

public checkbox(String title,String labels[],int orientation) {
  super();
  int length = labels.length;
  if(orientation == HORIZONTAL) setLayout(new GridLayout(1,length+1)); 
  else setLayout(new GridLayout(length+1,1));
  add(new Label(title));
  for(int i=0;i<length;++i) add(new Checkbox(labels[i])); 
 }

public checkbox(String title,String labels[],boolean state[],
  int orientation) {
  super();
  int length = labels.length;
  if(orientation == HORIZONTAL) setLayout(new GridLayout(1,length+1)); 
  else setLayout(new GridLayout(length+1,1));
  add(new Label(title));
  for(int i=0;i<length;++i){
   Checkbox checkBox = new Checkbox(labels[i]); 
   checkBox.setState(state[i]);
   add(checkBox);
 }
}
public boolean getState(String label) {
  Checkbox boxes[] = (Checkbox[])getComponents();
  for(int i=0;i<boxes.length;++i)
   if(label.equals(boxes[i].getLabel())) return boxes[i].getState();
  return false;
 }
 public void setState(String label,boolean state) {
  Checkbox boxes[] = (Checkbox[])getComponents();
  for(int i=0;i<boxes.length;++i)
   if(label.equals(boxes[i].getLabel())) boxes[i].setState(state); 
  }
 }