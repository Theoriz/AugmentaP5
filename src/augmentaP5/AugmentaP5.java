package augmentaP5;

import netP5.*;
import oscP5.*;
import processing.core.PApplet;
import processing.core.PVector;
import processing.core.PGraphics;
import java.util.*;
import java.lang.reflect.Method;
import java.lang.Integer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.awt.geom.Point2D;
import static java.lang.Math.toIntExact;

/**
 * Augmenta Connection: Connects to Augmenta app and provides your applet with
 * Augmenta People objects as they arrive. This library is based on the TSPS
 * library for processing. More details at http://www.tsps.cc/
 */

public class AugmentaP5 extends PApplet {
	//
	public static PApplet parent;
	private OscP5 receiver;
	
	private static int oscPort = 12000;

	/**
	 * Hashtable of current People objects. Accessibly by unique id, e.g.
	 * people.get( 0 )
	 */
	public Hashtable<Integer, AugmentaPerson> people;

	/**
	 * Current active list of People, copied to public list before every call to
	 * draw()
	 */
	private Hashtable<Integer, AugmentaPerson> _currentPeople;

	public InteractiveArea interactiveArea;

	public static PGraphics canvas;

	private Method personEntered;
	private Method personUpdated;
	private Method personWillLeave;
	private Method customEvent;
	
	private AugmentaScene scene;
	
	private int width = 0;
	private int height = 0;

	private static int timeOut = 120; // After this number of frames, a point
	// that hasn't been updated is
	// destroyed. Do not set under 0.

	// Holds a boolean to know if we're connected to an OSC port
	public static boolean isConnected = false;

	private static final Lock lock = new ReentrantLock();
	
	private static float smoothAmount = 0;

	/**
	 * Starts up Augmenta with the default port (12000). Will also attempt to
	 * set up default Augmenta events, so will look for three methods
	 * implemented in your app: void personEntered( AugmentaPerson p); void
	 * personUpdated( AugmentaPerson p); void personWillLeave( AugmentaPerson p);
	 * 
	 * @param PApplet
	 *            _parent Your app (pass in as "this")
	 */
	public AugmentaP5(PApplet _parent) {
		this(_parent, oscPort);
	}

	/**
	 * Starts up Augmenta with a specific port. The port must match what is
	 * specified in the Augmenta GUI. Will also attempt to set up default
	 * Augmenta events, so will look for three methods implemented in your app:
	 * void personEntered( AugmentaPerson p); void personUpdated( AugmentaPerson
	 * p); void personWillLeave( AugmentaPerson p);
	 * 
	 * @param PApplet
	 *            _parent Your app (pass in as "this")
	 * @param int Port set in Augmenta app
	 */

	public AugmentaP5(PApplet _parent, int port){
		// Common operations
		parent = _parent;
		interactiveArea = new InteractiveArea();
		scene = new AugmentaScene();
		people = new Hashtable<Integer, AugmentaPerson>();
		_currentPeople = new Hashtable<Integer, AugmentaPerson>();
		registerEvents();
		parent. registerMethod("pre", this);
		canvas = (PGraphics)(_parent.g);
		
		if (port <= 1024 || port > 65535){
			System.out.println("ERROR : port "+port+" is not allowed, switching back to default ("+oscPort);
		} else {
			oscPort = port;
		}

		createReceiver();
	}
	
	private void createReceiver(){
		System.out.println("[AugmentaP5] Starting the receiver with port ("+ oscPort + ")");
		receiver = new OscP5(this, oscPort);
	}
	
	public int getPort(){
		return oscPort;
	}

	public void setGraphicsTarget(PGraphics target)
	{
		canvas = target;
	}

	public void sendSimulation(AugmentaPerson p, NetAddress address, String message) {

		// Create the message
		OscMessage person = new OscMessage("/au/"+message);

		person.add(p.pid); // pid
		person.add(p.oid); // oid
		person.add(p.age); // age
		person.add(p.centroid.x); // centroid.x
		person.add(p.centroid.y); // centroid.y
		person.add(p.velocity.x); // velocity.x
		person.add(p.velocity.y); // velocity.y
		person.add(p.depth); // depth
		person.add(p.boundingRect.x); // boundingRect.x
		person.add(p.boundingRect.y); // boundingRect.y
		person.add(p.boundingRect.width); // boundingRect.width
		person.add(p.boundingRect.height); // boundingRect.height
		person.add(p.highest.x); // highest.x
		person.add(p.highest.y); // highest.y
		person.add(p.highest.z); // highest.z

		// Send the packet
		receiver.send(person, address);
	}

	public void sendSimulation(AugmentaPerson p, NetAddress address) {
		// Create the message
		sendSimulation(p, address, "personUpdated");
	}


	public void sendScene(int width, int height, int depth, int age, float percentCovered, int numPeople, Point2D.Float averageMotion, NetAddress address) {
		// Create the message
		OscMessage msg = new OscMessage("/au/scene");
		msg.add(age); // age
		msg.add(percentCovered); // percentage covered
		msg.add(numPeople); // number of people
		msg.add(averageMotion.x); // average motion X
		msg.add(averageMotion.y); // average motion Y
		msg.add(width); // width in pixels
		msg.add(height); // height in pixels
		msg.add(depth); // depth in pixels
		// Send the packet
		receiver.send(msg, address);
	}
	public void sendScene(int width, int height, NetAddress address) {
		Point2D.Float zero = new Point2D.Float(0f,0f);
		sendScene(width, height, 0, 0, 0f, 0, zero, address);
	}

	public void unbind() {
		System.out.println("[AugmentaP5] AugmentaP5 object unbinding...");
		if (receiver != null){
			receiver.stop();
			receiver = null;
		}
		people.clear();
		_currentPeople.clear();
	}

	public void finalize() {
		System.out.println("[AugmentaP5] AugmentaP5 object terminating...");
		unbind();
		people = null;
		_currentPeople = null;
	}
	
	public void reconnect(int _port){
		if(_port != oscPort ){
		      unbind();
		      oscPort = _port;
		      createReceiver();
		}
	}

	public void pre() {
		
		// get enumeration, which helps us loop through Augmenta.people
		Enumeration e = _currentPeople.keys();

		// loop through people + copy all to public hashtable
		people.clear();

		lock.lock();

		while (e.hasMoreElements()) {
			// get person
			int id = (Integer) e.nextElement();
			AugmentaPerson person = (AugmentaPerson) _currentPeople.get(id);

			// Adding this test to counteract nullPointerExceptions ocurring in
			// rare cases
			if (person != null) {
				person.lastUpdated--;
				// haven't gotten an update in a given number of frames
				if (person.lastUpdated < -1) {
					System.out.println("[AugmentaP5] Person deleted because it has not been updated for 120 frames");
					callPersonWillLeave(person);
					_currentPeople.remove(person.pid);
				} else {
					AugmentaPerson p = new AugmentaPerson();
					p.copy(person);
					people.put(p.pid, p);
				}
			}	
		}

		lock.unlock();
		
	}

	/**
	 * Access the current People objects as an array instead of a Hashmap
	 * 
	 * @return Array of AugmentaPerson objects
	 */
	public AugmentaPerson[] getPeopleArray() {
		return (AugmentaPerson[]) (people.values()
				.toArray(new AugmentaPerson[people.values().size()]));
	}

	/**
	 * @return Current number of people
	 */
	public int getNumPeople() {
		return people.size();
	}

	// Update a person
	private static void updatePerson(AugmentaPerson p, OscMessage theOscMessage) {
		lock.lock();
		try {
			p.pid = theOscMessage.get(0).intValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [0] should be an int (id)");
		}
		try {
			p.oid = theOscMessage.get(1).intValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [1] should be an int (oid)");
		}
		try {
			p.age = theOscMessage.get(2).intValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [2] should be an int (age)");
		}
		try {
			p.centroid.x = theOscMessage.get(3).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [3] should be a float (centroid.x)");
		}
		try {
			p.centroid.y = theOscMessage.get(4).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [4] should be a float (centroid.y)");
		}
		try {
			p.velocity.x = theOscMessage.get(5).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [5] should be a float (velocity.x)");
		}
		try {
			p.velocity.y = theOscMessage.get(6).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [6] should be a float (velocity.y)");
		}
		try {
			p.depth = theOscMessage.get(7).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [7] should be a float (depth)");
		}
		try {
			p.boundingRect.x = theOscMessage.get(8).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [8] should be a float (boundignRect.x)");
		}
		try {
			p.boundingRect.y = theOscMessage.get(9).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [9] should be a float (boundignRect.y)");
		}
		try {
			p.boundingRect.width = theOscMessage.get(10).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [10] should be a float (boundignRect.width)");
		}
		try {
			p.boundingRect.height = theOscMessage.get(11).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [11] should be a float (boundignRect.height)");
		}
		try {
			p.highest.x = theOscMessage.get(12).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [12] should be a float (highest.x)");
		}
		try {
			p.highest.y = theOscMessage.get(13).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [13] should be a float (highest.y)");
		}
		try {
			p.highest.z = theOscMessage.get(14).floatValue();
		} catch (Exception e) {
			System.out
			.println("[AugmentaP5] The OSC message with address 'updatedPerson' could not be parsed : the value [14] should be a float (highest.z)");
		}

		/*
		 * Old protocol p.haarRect.x = theOscMessage.get(14).floatValue();
		 * p.haarRect.y = theOscMessage.get(15).floatValue(); p.haarRect.width =
		 * theOscMessage.get(16).floatValue(); p.haarRect.height =
		 * theOscMessage.get(17).floatValue(); p.opticalFlow.x =
		 * theOscMessage.get(18).floatValue(); p.opticalFlow.y =
		 * theOscMessage.get(19).floatValue();
		 */

		// Values 15 to 19 are free for other data

		p.contours.clear();
		for (int i = 20; i < theOscMessage.arguments().length; i += 2) {
			PVector point = new PVector();
			point.x = theOscMessage.get(i).floatValue();
			point.y = theOscMessage.get(i + 1).floatValue();
			p.contours.add(point);
		}
		
		if(smoothAmount != 0){
			p.smooth(smoothAmount);
		}
		
		p.lastUpdated = timeOut;
		lock.unlock();
	}

	// Set up (optional) Augmenta Events
	private void registerEvents() {
		// check to see if the host applet implements methods:
		// public void personEntered(AugmentaPerson p)
		// public void personEntered(AugmentaPerson p)
		// public void personEntered(AugmentaPerson p)
		// public void customEvent(ArrayList<String> args)
		try {
			personEntered = parent.getClass().getMethod("personEntered",
					new Class[] { AugmentaPerson.class });
			personUpdated = parent.getClass().getMethod("personUpdated",
					new Class[] { AugmentaPerson.class });
			personWillLeave = parent.getClass().getMethod("personWillLeave",
					new Class[] { AugmentaPerson.class });
			customEvent = parent.getClass().getMethod("customEvent",
					new Class[] { ArrayList.class });
		} catch (Exception e) {
			// no such method, or an error.. which is fine, just ignore
		}
	}

	// Parse incoming OSC Message
	protected void oscEvent(OscMessage theOscMessage) {
		// adding a person
		if (theOscMessage.checkAddrPattern("/au/personEntered")
				|| theOscMessage.checkAddrPattern("/au/personEntered/")) {
			AugmentaPerson p = new AugmentaPerson();

			// Get the point's coordinates
			PVector point = new PVector(-1f, -1f);
			try {
				point.x = theOscMessage.get(3).floatValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address  'personEntered' could not be parsed : the value [3] should be a float (centroid.x)");
			}
			try {
				point.y = theOscMessage.get(4).floatValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address  'personEntered' could not be parsed : the value [4] should be a float (centroid.y)");
			}

			// Check if the point is inside the interactive area first
			if(interactiveArea.contains(point)){
				updatePerson(p, theOscMessage);
				_currentPeople.put(p.pid, p);
				callPersonEntered(p);
			}

			// updating a person (or adding them if they don't exist in the
			// system yet)
		} else if (theOscMessage.checkAddrPattern("/au/personUpdated")
				|| theOscMessage.checkAddrPattern("/au/personUpdated/")) {

			AugmentaPerson p = null;
			try {
				p = _currentPeople.get(theOscMessage.get(0).intValue());
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address  'personUpdated' could not be parsed : the value [0] should be an int (id)");
			}

			// Get the point's coordinates
			PVector point = new PVector(-1f, -1f);
			try {
				point.x = theOscMessage.get(3).floatValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address  'personUpdated' could not be parsed : the value [3] should be a float (centroid.x)");
			}
			try {
				point.y = theOscMessage.get(4).floatValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address  'personUpdated' could not be parsed : the value [4] should be a float (centroid.y)");
			}

			// Check if the person exists in the scene
			boolean personExists = (p != null);

			// Check if the point is inside the interactive area
			if(interactiveArea.contains(point)){
				
				if (!personExists) {
					p = new AugmentaPerson();
					updatePerson(p, theOscMessage);
					callPersonEntered(p);
					_currentPeople.put(p.pid, p);
				} else {
					updatePerson(p, theOscMessage);
					callPersonUpdated(p);
				}
			} else {
				// Else we have to act like that the person left
				if (personExists) {
					updatePerson(p, theOscMessage);
					callPersonWillLeave(p);
					_currentPeople.remove(p.pid);
				} // if the person does not exist in the scene no need to do this again
			}

		}

		// person is about to leave
		else if (theOscMessage.checkAddrPattern("/au/personWillLeave")
				|| theOscMessage.checkAddrPattern("/au/personWillLeave/")) {

			AugmentaPerson p = null;
			try {
				p = _currentPeople.get(theOscMessage.get(0).intValue());
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'personWillLeave' could not be parsed : the value [0] should be an int (id)");
			}
			if(p == null){
				System.out.println("ERROR : no person found with id "+theOscMessage.get(0).intValue());
				return;
			}
			updatePerson(p, theOscMessage);

			callPersonWillLeave(p);
			_currentPeople.remove(p.pid);
		}

		// scene
		else if (theOscMessage.checkAddrPattern("/au/scene")) {
			try {
				scene.age = theOscMessage.get(0).intValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'scene' could not be parsed : the value [0] should be an int (age)");
			}
			try {
				scene.percentCovered = theOscMessage.get(1).floatValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'scene' could not be parsed : the value [1] should be a float (percentCovered)");
			}
			try {
				scene.numPeople = theOscMessage.get(2).intValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'scene' could not be parsed : the value [2] should be an int (numPeople)");
			}
			try {
				scene.averageMotion.x = theOscMessage.get(3).floatValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'scene' could not be parsed : the value [3] should be a float (averageMotion.x)");
			}
			try {
				scene.averageMotion.y = theOscMessage.get(4).floatValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'scene' could not be parsed : the value [4] should be a float (averageMotion.y)");
			}
			try {
				width = theOscMessage.get(5).intValue();
				scene.width = theOscMessage.get(5).intValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'scene' could not be parsed : the value [5] should be an int (width)");
			}
			try {
				height = theOscMessage.get(6).intValue();
				scene.height = theOscMessage.get(6).intValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'scene' could not be parsed : the value [6] should be an int (height)");
			}
			/*try {
				scene.depth = theOscMessage.get(7).intValue();
			} catch (Exception e) {
				System.out
				.println("[AugmentaP5] The OSC message with address 'scene' could not be parsed : the value [7] should be an int (depth)");
			}*/

			// System.out.println("[Augmenta] Received OSC OK : width "+width+" height "+height);
		}

		// custom event
		else if (theOscMessage.checkAddrPattern("/au/customEvent")
				|| theOscMessage.checkAddrPattern("/au/customEvent/")) {
			ArrayList<String> args = new ArrayList<String>();
			for (int i = 0; i < theOscMessage.arguments().length; i++) {
				args.add(theOscMessage.get(i).stringValue());
			}
			callCustomEvent(args);
		}
	}

	private void callPersonEntered(AugmentaPerson p) {
		if (personEntered != null) {
			try {
				personEntered.invoke(parent, new Object[] { p });
			} catch (Exception e) {
				System.err
				.println("[AugmentaP5] Disabling personEntered() for Augmenta because of an error.");
				e.printStackTrace();
				personEntered = null;
			}
		}
	}
	
	private void callPersonUpdated(AugmentaPerson p) {
		if (personUpdated != null) {
			try {
				personUpdated.invoke(parent, new Object[] { p });
			} catch (Exception e) {
				System.err
				.println("[AugmentaP5] Disabling personUpdated() for Augmenta because of an error.");
				e.printStackTrace();
				personUpdated = null;
			}
		}
	}
	
	private void callPersonWillLeave(AugmentaPerson p) {
		if (personWillLeave != null) {
			try {
				personWillLeave.invoke(parent, new Object[] { p });
			} catch (Exception e) {
				System.err
				.println("[AugmentaP5] Disabling personWillLeave() for Augmenta because of an error.");
				e.printStackTrace();
				personWillLeave = null;
			}
		}
	}
	
	private void callCustomEvent(ArrayList<String> strings) {
		if (customEvent != null) {
			try {
				customEvent.invoke(parent, new Object[] { strings });
			} catch (Exception e) {
				System.err
				.println("[AugmentaP5] Disabling customEvent() for Augmenta because of an error.");
				e.printStackTrace();
				customEvent = null;
			}
		}
	}
	
	public int[] getSceneSize() {
		int[] res = new int[2];
		res[0] = width;
		res[1] = height;
		if (width == 0 || height == 0) {
			// System.out.println("[AugmentaP5 Warning : at least one of the dimensions is null or equal to 0");
		}
		return res;
	
	}
	
	public void setTimeOut(int n) {
		if (n >= 0) {
			timeOut = n;
		}
	}
	
	public AugmentaPerson getOldestPerson(){
		int bestAge = 0;
		int bestPerson = -1;
		// For each person...
		for (int key : people.keySet()) {
			PVector pos = people.get(key).centroid;
			if (people.get(key).age > bestAge) {
				bestAge = people.get(key).age;
				bestPerson = key;
			} 
			// If several persons have the same oldest age, take the one with smallest pid
			else if (people.get(key).age == bestAge){
				if(people.get(key).pid < people.get(bestPerson).pid){
					bestPerson = key;
				}
			}
		}
		AugmentaPerson p = null;
		if (bestPerson != -1){
			p = people.get(bestPerson);
		}	
		return p;
	}
	
	public AugmentaPerson getNewestPerson(){
		int bestAge = Integer.MAX_VALUE;
		int bestPerson = -1;
		// For each person...
		for (int key : people.keySet()) {
			PVector pos = people.get(key).centroid;
			if (people.get(key).age < bestAge) {
				bestAge = people.get(key).age;
				bestPerson = key;
			}
			// If several persons have the same newest age, take the one with greatest pid
			else if (people.get(key).age == bestAge){
				if(people.get(key).pid > people.get(bestPerson).pid){
					bestPerson = key;
				}
			}
		}
		AugmentaPerson p = null;
		if (bestPerson != -1){
			p = people.get(bestPerson);
		}	
		return p;
	}
	
	public void setSmooth(float amount){
		// Check amount bounds
		if(amount < 0){
			smoothAmount = 0;
		} else if(amount > 0.99){
			smoothAmount = 0.99f;
		} else {
			smoothAmount = amount;
		}
	}
	
	public AugmentaScene getScene(){
		return scene;
	}

};