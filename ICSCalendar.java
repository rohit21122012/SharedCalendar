
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Scanner;

import org.apache.commons.lang.time.DateUtils;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.filter.Filter;
import net.fortuna.ical4j.filter.PeriodRule;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.IndexedComponentList;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.util.UidGenerator;

//Class ICSCalendar stores the calendar and various methods related to the calendar
public class ICSCalendar {
	private static int eventCount = 0;
	private String FileName;
	private Calendar myCalendar;
	private int isonline;
	private WebDavConnector Connection;
	public int isBoss;
	private Scanner scan; 
	
	public ICSCalendar(String fileName) {
		FileName = fileName;
		Connection = new WebDavConnector();
		scan = new Scanner(System.in);
		//Create a new calendar
		try {
			FileInputStream fin = new FileInputStream(fileName);
			CalendarBuilder cb = new CalendarBuilder();
			try {
				myCalendar = cb.build(fin);
			} catch (IOException | ParserException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			//e.printStackTrace();
			myCalendar = new Calendar();
			myCalendar.getProperties().add(new ProdId("-//IITMandi //Calendar using iCal4j 1.0//EN"));
			myCalendar.getProperties().add(net.fortuna.ical4j.model.property.Version.VERSION_2_0);
			myCalendar.getProperties().add(CalScale.GREGORIAN);
			this.addDayEvent(0, 0, 0, 0, 0, "NULL Event");
		}	
		isonline = 0;
		isBoss = 0;
	}
	
	//sets online status
	public void SetOnline(int i){    
		isonline = i;
	}
	
	public void SetBoss(int i){
		isBoss = i;
	}
	
	//ResolveConflict should be called only when online
	@SuppressWarnings("unchecked")
	public void ResolveConflict(){
		String remoteCalFilePath = "http://localhost/webdav/" + FileName;
		java.util.Date m1,m2;
		String token;
			//locking the folder because if we do not put lock,
		//Problem: between exists check and putCalendar if somebody else calls putCalendar.
		String lockFilePath = "http://localhost/webdav/lockFile/";
		if(Connection.exists(lockFilePath) == false)
			Connection.putLockFile(lockFilePath);
		token = Connection.lock(lockFilePath);
		if(Connection.exists(remoteCalFilePath) == false){
			Connection.putCalendar(FileName);
			Connection.unlock(lockFilePath, token);
			this.ResolveConflict();
			return;
		}

//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		
		Connection.unlock(lockFilePath, token);
		int z;  //z is used later in the method
		//sync
		while(true){
			z = 1;
			
			//locking the file so as to keep the file and its time stamp consistent
			token = Connection.lock(lockFilePath);

			Connection.getCalendar(FileName);
			//time stamp when getting file from server
			m1 = Connection.GetModifiedDate(remoteCalFilePath);
			Connection.unlock(lockFilePath, token);
			
			//code to make an Calendar object from the file stream obtained from server
			FileInputStream fin = null;
			try {
				fin = new FileInputStream(FileName);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			CalendarBuilder builder = new CalendarBuilder();
			Calendar remoteCalendar = null;
			try {
				remoteCalendar = builder.build(fin);
			} catch (IOException | ParserException e) {
				e.printStackTrace();
			}
			
			//to resolve conflict between calendar  and remoteCalendar : 
			//1. Update the status of events common in local and server. 
			//2. Add the events not present in local but present in server.
			
			java.util.Calendar startDate = new GregorianCalendar();
			
			
			java.util.Calendar endDate = new GregorianCalendar();
			endDate.set(java.util.Calendar.MONTH, startDate.get(java.util.Calendar.MONTH) + 1);
			endDate.set(java.util.Calendar.DAY_OF_MONTH, startDate.get(java.util.Calendar.DAY_OF_MONTH) +1);
			endDate.set(java.util.Calendar.YEAR, startDate.get(java.util.Calendar.YEAR));
			
			DateTime start = new DateTime(startDate.getTime());
			DateTime end = new DateTime(endDate.getTime());
			
			Period period = new Period(start, end);
			Filter filter = new Filter(new PeriodRule(period));

			ComponentList l = (ComponentList)filter.filter(myCalendar.getComponents(Component.VEVENT));
			if(l.size() == 0){
				System.out.println("hey this is empty");
			}
			ComponentList r = (ComponentList)filter.filter(remoteCalendar.getComponents(Component.VEVENT));
			
			//IndexedComponentList gives ComponentList indexed with the property mentioned,
			// in our case UID.
			IndexedComponentList inl = new IndexedComponentList(l, Property.UID);
			
			//r_l will store the events present in r but not in l
			ComponentList r_l = new ComponentList();
			
			
						
			for (Object obj : r){
				Uid tempUid = ((VEvent)obj).getUid();
				Component com = inl.getComponent(tempUid.getValue());		//event corresponding to obj in l

				if(com == null){			//if new event from remote
					if(isBoss == 1){		//to make  (to be cancelled events) cancelled
//						if(((VEvent)obj).getStatus() == Status.VTODO_NEEDS_ACTION){
//							((VEvent)obj).getProperties().remove(((VEvent)obj).getStatus());
//							((VEvent)obj).getProperties().add(Status.VEVENT_CANCELLED);
//						}
					}
					r_l.add(obj);
				}
				else{
					if(isBoss == 1){
						//All the common events in the local copy of boss and the remote copy will be either 
						//(CONFIRMED or CANCELLED by the boss) or  
//						if(((VEvent)obj).getStatus() == Status.VTODO_NEEDS_ACTION){
//							((VEvent)com).getProperties().remove(((VEvent)com).getStatus());
//							((VEvent)com).getProperties().add(Status.VEVENT_CANCELLED);
//						}
					}
					else{
						//if local events status is not equal to remotes
						if(((VEvent)com).getStatus() != ((VEvent)obj).getStatus()){

							((VEvent)com).getProperties().remove(((VEvent)com).getStatus());
							((VEvent)com).getProperties().add(((VEvent)obj).getStatus());
						}

					}
					
				}
			}
			for (Object object : r_l) {
				VEvent newEvent = (VEvent)object;
				myCalendar.getComponents().add(newEvent);
				//myCalendar.getComponents(Component.VEVENT).add(newEvent);
			}
			
//			if(myCalendar.getComponents(Component.VEVENT).addAll(r_l) == true){
//				System.out.println("myCalendar is modified with remote content");
//			}
			
			if(isBoss == 1){
				

				//filter will give next one month events
				ComponentList eventsInNextOneMonth = (ComponentList)filter.filter(myCalendar.getComponents(Component.VEVENT));

				if(eventsInNextOneMonth.size() == 0){
					System.out.println("\nNo events in next one month.");
				}
				else{
					System.out.println("\nEvents scheduled to happen over next one month: ");
					//IndexedComponentList used to sort the events by Start date of the event
					IndexedComponentList eventsSortedByDate = new IndexedComponentList(eventsInNextOneMonth, Property.DTSTART);
					
					ComponentList eventsOnDate = new ComponentList();
					//ComponentList eventsOnDateDuplicate; //to avoid ConcurrentModificationExecution which happen in the loop below
					//To iterate over from startDate to endDate
					//To print all the events in the next one month whose status is not cancelled sorted by date
					java.util.Date date = startDate.getTime();
					date.setMinutes(0);
					date.setSeconds(0);
							
					
					ComponentList eventsToChooseFrom = new ComponentList();
					for (; !date.after(endDate.getTime()); date.setHours(date.getHours()+1)) 
			        {
						//System.out.println(date);
						//All the events on the date passed as argument returned as a list
						eventsOnDate = eventsSortedByDate.getComponents((new DateTime(date)).toString());
						
						
//						for(Object obj : eventsOnDate1){
//							if(((VEvent)(obj)).getStatus().equals(Status.VEVENT_CANCELLED) == false){
//								eventsOnDate.add(obj);
//							}
//						}
						//System.out.println("Size is " + eventsOnDate.size());
						
						for(int i = 0; i < eventsOnDate.size(); i++){
							VEvent eventWithSameDate = (VEvent) eventsOnDate.get(i);
							if(eventWithSameDate.getStatus().equals(Status.VEVENT_CANCELLED) == true){
								eventsOnDate.remove(eventWithSameDate);
							//	System.out.println("Size now is " + eventsOnDate.size());
							}
						}
//							if(((VEvent)(obj)).getStatus().equals(Status.VEVENT_CANCELLED) == false){
//								eventsOnDate.add(obj);
//							}
//						}
						SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy EEE ");
			            if(eventsOnDate.size() > 1){
							
							for(Object e : eventsOnDate){
								VEvent temp = (VEvent) e;
								String eventDate = formatter.format(temp.getStartDate().getDate().getTime());
								System.out.println(z + ". " +temp.getStatus().getValue() + " " +  eventDate + " " + temp.getSummary().toString());

								//System.out.println("\n" + z + "\tstart: " + ((VEvent)e).getStartDate() + "  end: " + ((VEvent)e).getEndDate() + "\t" + ((VEvent)e).getSummary());
								((VEvent)e).getProperties().remove(((VEvent)e).getStatus());
								((VEvent)e).getProperties().add(Status.VEVENT_CANCELLED);
								eventsToChooseFrom.add(e);
								z = z + 1;
				            }
							System.out.println("These events are conflicting, enter the eventno of the event you want to attend: ");
						
							z = this.scan.nextInt();
							while(z<=0 && z > eventsOnDate.size()){
								System.out.println("Re-enter the Event No appropriately");
								z = this.scan.nextInt();
							}
							((VEvent)eventsToChooseFrom.get(z-1)).getProperties().remove(((VEvent)eventsToChooseFrom.get(z-1)).getStatus());
							((VEvent)eventsToChooseFrom.get(z-1)).getProperties().add(Status.VEVENT_CONFIRMED);
						
						}
			        }
				}
			}
			//System.out.println("Generating file");
			//updating the local file from the myCalendar
			this.generateFile();
			
			//System.out.println("File generated");
			//lock file before calculating time stamp because time stamp gets invalid if file is modified
			//after calculating time stamp.
			token = Connection.lock(lockFilePath);
			m2 = Connection.GetModifiedDate(remoteCalFilePath);
			//System.out.println("\nm2: " + m2);
			//if file on server was not modified while sync, then put the consistent copy on the server 
			//else again sync with the modified file on server

			if(m1.equals(m2)){   
				Connection.putCalendar(FileName);
				Connection.unlock(lockFilePath, token);
				break;
			}
			else{
				Connection.unlock(lockFilePath, token);
				System.out.println("Time Stamp has changed");
			}
		
		}//end of while
		
	}
	
	public void printCalendar() {
		if(isonline == 1){
			this.ResolveConflict();
		}
		System.out.println(myCalendar);
	}
	
	public void addDayEvent(int startTime, int endTime, int dayofmonth, int month, int year, String description){
		if(isonline == 1){
			this.ResolveConflict();
		}
		
		//adding the event in the object
		java.util.Calendar startDate = new GregorianCalendar();
		startDate.set(java.util.Calendar.MONTH, month-1);
		startDate.set(java.util.Calendar.DAY_OF_MONTH, dayofmonth);
		startDate.set(java.util.Calendar.YEAR, year);
		startDate.set(java.util.Calendar.HOUR_OF_DAY, startTime);
		startDate.set(java.util.Calendar.MINUTE, 0);
		startDate.set(java.util.Calendar.SECOND, 0);

	
		java.util.Calendar endDate = new GregorianCalendar();
		endDate.set(java.util.Calendar.MONTH, month-1);
		endDate.set(java.util.Calendar.DAY_OF_MONTH, dayofmonth);
		endDate.set(java.util.Calendar.YEAR, year);
		endDate.set(java.util.Calendar.HOUR_OF_DAY, endTime);
		endDate.set(java.util.Calendar.MINUTE, 0);	
		endDate.set(java.util.Calendar.SECOND, 0);
		
		DateTime start = new DateTime(startDate.getTime());
		DateTime end = new DateTime(endDate.getTime());
		VEvent event = new VEvent(start, end, description);
		//adding the event with status as TENTATIVE
		if(isBoss == 1)
			event.getProperties().add(Status.VEVENT_CONFIRMED);
		else
			event.getProperties().add(Status.VEVENT_TENTATIVE);
		
		UidGenerator ug;
		try {
			ug = new UidGenerator(Integer.toString(eventCount));
			event.getProperties().add(ug.generateUid());
			System.out.println("\nid: " + ug.generateUid() + "\n");
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		
		//Adding the event to the core object
		myCalendar.getComponents().add(event);
		eventCount += 1;
		
		this.generateFile();
		System.out.println("FIle generated");
		
		
	}
	
	public void CancelEvent(int startTime, int endTime, int dayofmonth, int month, int year){

		java.util.Calendar startDate = new GregorianCalendar();
		startDate.set(java.util.Calendar.MONTH, month-1);
		startDate.set(java.util.Calendar.DAY_OF_MONTH, dayofmonth);
		startDate.set(java.util.Calendar.YEAR, year);
		startDate.set(java.util.Calendar.HOUR_OF_DAY, startTime);
		startDate.set(java.util.Calendar.MINUTE, 0);
		startDate.set(java.util.Calendar.SECOND, 0);

		java.util.Calendar endDate = new GregorianCalendar();
		endDate.set(java.util.Calendar.MONTH, month-1);
		endDate.set(java.util.Calendar.DAY_OF_MONTH, dayofmonth);
		endDate.set(java.util.Calendar.YEAR, year);
		endDate.set(java.util.Calendar.HOUR_OF_DAY, endTime);
		endDate.set(java.util.Calendar.MINUTE, 0);	
		endDate.set(java.util.Calendar.SECOND, 0);
		
		DateTime start = new DateTime(startDate.getTime());
		DateTime end = new DateTime(endDate.getTime());
	
		Period period = new Period(start, end);
		Filter filter = new Filter(new PeriodRule(period));

		ComponentList eventsToday1 = (ComponentList)filter.filter(myCalendar.getComponents(Component.VEVENT));
		ComponentList eventsToday = new ComponentList();
		
		//eventsToday is formed by removing 'cancelled' events from eventsToday1
		for(Object obj : eventsToday1){
			if(((VEvent)(obj)).getStatus().equals(Status.VEVENT_CANCELLED) == false){
				eventsToday.add(obj);
			}
		}
		
		SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy EEE ");
		//If there are more than 1 events in that period
		if(eventsToday.size() > 1){
			System.out.println("\nThere are multiple events in this period: ");
			int z = 1;
			for(Object obj : eventsToday){
				VEvent temp = (VEvent) obj;
				String eventDate = formatter.format(temp.getStartDate().getDate().getTime());
				System.out.println(z + ". " +temp.getStatus().getValue() + " " +  eventDate + " " + temp.getSummary().toString());

				z = z + 1;
			}
			if(isBoss == 1)
				System.out.println("\n Do you want to cancel all the events ?(Yes-1, No-0): ");
			else
				System.out.println("\n Do you want to request cancel for all the events ?(Yes-1, No-0): ");

			z = scan.nextInt();
			if(z == 1){
				if(isBoss == 1){
					for(Object obj : eventsToday){
						((VEvent)(obj)).getProperties().remove(((VEvent)(obj)).getStatus());
						((VEvent)(obj)).getProperties().add(Status.VEVENT_CANCELLED);
					}
				}
				else{
					for(Object obj : eventsToday){
						((VEvent)(obj)).getProperties().remove(((VEvent)(obj)).getStatus());
						((VEvent)(obj)).getProperties().add(Status.VTODO_NEEDS_ACTION);
					}
				}
			}
			else{
				int y;
				if(isBoss == 1){
					System.out.println("Enter the event nos of the events which you want to cancel(0 to exit): ");
					while(true){
						y = scan.nextInt();
						if(y == 0){
							break;
						}
						if(y > eventsToday.size()){
							System.out.println("Enter a valid no");
							continue;
						}
						((VEvent)(eventsToday.get(y-1))).getProperties().remove( ((VEvent)(eventsToday.get(y-1))).getStatus() );
						((VEvent)(eventsToday.get(y-1))).getProperties().add( Status.VEVENT_CANCELLED );
					}
				}
				else{
					System.out.println("Enter the event nos of the events for which you want to request cancel(0 to exit): ");
					while(true){
						y = scan.nextInt();
						if(y > eventsToday.size()){
							System.out.println("Enter a valid no");
							continue;
						}
						((VEvent)(eventsToday.get(y-1))).getProperties().remove( ((VEvent)(eventsToday.get(y-1))).getStatus() );
						((VEvent)(eventsToday.get(y-1))).getProperties().add( Status.VTODO_NEEDS_ACTION);
					}
				}
			}
		}
		else if(eventsToday.size() == 0){
			System.out.println("\nNo event in this period");
			return;
		}
		else if(eventsToday.size() == 1){
			VEvent tempEvent = (VEvent)(eventsToday.get(0));
			tempEvent.getProperties().remove(tempEvent.getStatus());
			if(isBoss == 1){
				tempEvent.getProperties().add(Status.VEVENT_CANCELLED);
			}
			else{
				tempEvent.getProperties().add(Status.VEVENT_TENTATIVE);
			}
		}
		this.generateFile();
		if(isonline == 1){
			this.ResolveConflict();
		}
	}
	
	public void seeEventsBetween(int startDay, int startMonth, int startYear, int endDay, int endMonth, int endYear){
		if(isonline == 1){
			this.ResolveConflict();
		}		
			//start date for the time period

		java.util.Calendar startDate = new GregorianCalendar();
		startDate.set(java.util.Calendar.MONTH, startMonth-1);
		startDate.set(java.util.Calendar.DAY_OF_MONTH, startDay);
		startDate.set(java.util.Calendar.YEAR, startYear);
		startDate.set(java.util.Calendar.HOUR_OF_DAY, 0);
		startDate.set(java.util.Calendar.MINUTE, 0);
		startDate.set(java.util.Calendar.SECOND, 0);

			//end date for the time period
		java.util.Calendar endDate = new GregorianCalendar();
		endDate.set(java.util.Calendar.MONTH, endMonth-1);
		endDate.set(java.util.Calendar.DAY_OF_MONTH, endDay+1);
		endDate.set(java.util.Calendar.YEAR, endYear);
		endDate.set(java.util.Calendar.HOUR_OF_DAY, 0);
		endDate.set(java.util.Calendar.MINUTE, 0);	
		endDate.set(java.util.Calendar.SECOND, 0);
		
		DateTime start = new DateTime(startDate.getTime());
		DateTime end = new DateTime(endDate.getTime());
		
		Period period = new Period(start, end);
		
			//Filtering the events in the period
		Filter filter = new Filter(new PeriodRule(period));

		ComponentList eventsBetween = (ComponentList)filter.filter(myCalendar.getComponents(Component.VEVENT));
		System.out.println("\nEvents between this period: ");

		
		SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy EEE ");
		
		int i = 1;
			//Printing each event
		for (Object object : eventsBetween) {
			//System.out.println(object);
			VEvent v = (VEvent)object;
			
			String date = formatter.format(v.getStartDate().getDate().getTime());
			System.out.println(i + ". " +v.getStatus().getValue() + " " +  date + " " + v.getSummary().toString());
			i+=1;
		}
	}

	public void generateFile(){
			//Connection.delCalendar(FileName);
		FileOutputStream fout;
		try {
			fout = new FileOutputStream(FileName);
			CalendarOutputter outputter = new CalendarOutputter();
			outputter.output(myCalendar, fout);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ValidationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}