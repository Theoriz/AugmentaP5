/**
*
*    * Augmenta simulator
*    Send some generated Augmenta Packet
*    Use your mouse to send custom packets
*
*    Author : David-Alexandre Chanel
*             Tom Duchêne
*
*    Website : http://www.theoriz.com
*
*/

import netP5.*;
import g4p_controls.*;

AugmentaP5 augmenta;
NetAddress sendingAddress;
AugmentaPerson testPerson;
GTextField portInput;
GButton portInputButton;

float x = 0;
float y = 0;
float t = 0; // time
int age = 0;
int direction = 1;
int pid = int(random(1000));
//int pid = 43;
int oscPort = 12000;

Boolean send = true;
Boolean moving = false;

void setup() {
  size(640,480);
  frameRate(30);
  
  // Osc network com
  augmenta = new AugmentaP5(this, 50000);
  sendingAddress = new NetAddress("127.0.0.1",oscPort);
  RectangleF rect = new RectangleF(0.4f,0.4f,0.2f,0.2f);
  PVector pos = new PVector(0.5f, 0.5f);
  testPerson = new AugmentaPerson(pid, pos, rect);
  
  // Set the UI
  portInput = new GTextField(this, 10, 55, 60, 20);
  portInputButton = new GButton(this, 70, 55, 110, 20, "Change Osc Port");
  portInput.setText(""+oscPort);
  G4P.registerSketch(this);

  // Init
  y=height/2;
  x= width/2;
}

void draw() {

  background(0);
  textSize(14);
  text("Drag mouse to send custom data to 127.0.0.1:"+oscPort,10,20);
  text("Press S to toggle data sending",10,35);
  text("Press M to toggle automatic movement",10,50);
  
  if(!mousePressed)
  {
    // Sin animation
    if (moving){
      x = map(sin(t),-1,1,width/10,width*9/10);
    }
  }
  // Draw disk
  if (send){
    fill(255);
  }else{
    fill(128);
  }
  ellipse(x,y,20,20);
  //rect(
  textSize(16);
  text(""+pid, x+20,y-10, 50, 20);

  // Increment val
  t= t + direction*TWO_PI/70; // 70 inc
  t = t % TWO_PI;
  age++;

  // Update point
  testPerson.centroid.x = (float)x/width;
  testPerson.centroid.y = (float)y/height;
  testPerson.boundingRect.x = (float)x/width-0.1;
  testPerson.boundingRect.y = (float)y/height-0.1;
  // Other values 
  testPerson.age = age;
  
  // Send point
  if (send){
    augmenta.send(testPerson, sendingAddress);
  }
  // Send scene
  augmenta.sendScene(0, 0, sendingAddress);

}

void mouseDragged() {
 
 // Update coords
 x = mouseX;
 y = mouseY;
 
 // The following code is here just for pure fun and aesthetic !
 // It enables the point to go on in its sinus road where
 // you left it !
 
 // Clamping
 if(x>width*9/10)
 {
   x=width*9/10;
 }
 if(x<width/10)
 {
   x=width/10;
 }
 // Reverse
 t = asin(map(x,width/10,width*9/10,-1,1));
 // Don't do it visually
 x = mouseX;
 // Change direction by calculating speed vector
 if(mouseX - pmouseX < 0)
 {
   direction = -1;
 } else {
   direction = 1;
 }
}

void keyPressed() {

  // Stop/Start the movement of the point
  if (key == 'm') {
    moving=!moving;
  } else if (key == 's'){
    send=!send;
    if (send){
      augmenta.send(testPerson, sendingAddress, "personEntered");
    } else {
      augmenta.send(testPerson, sendingAddress, "personWillLeave");
    }
    pid = int(random(1000));
    age = 0;
  } else if (key == ENTER || key == RETURN){
    if(portInput.hasFocus() == true) {
      handlePortInputButton();
    }
  }
}

public void handleButtonEvents(GButton button, GEvent event) { 
  if (button == portInputButton) {
    handlePortInputButton();
  }
}

public void handlePortInputButton() {

  if (Integer.parseInt(portInput.getText()) != oscPort) {
    println("input :"+portInput.getText());
    oscPort = Integer.parseInt(portInput.getText());
    augmenta.unbind();
    augmenta=null;
    augmenta= new AugmentaP5(this, 50000);
    sendingAddress = new NetAddress("127.0.0.1",oscPort);
  }
}
