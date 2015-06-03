
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
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
	
	public ICSCalendar(String fileName) {
		FileName = fileName;
		Connection = new WebDavConnector();
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
		}
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		
		Connection.unlock(lockFilePath, token);

		//sync
		while(true){
			
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
						if(((VEvent)obj).getStatus() == Status.VTODO_NEEDS_ACTION){
							((VEvent)obj).getProperties().remove(((VEvent)obj).getStatus());
							((VEvent)obj).getProperties().add(Status.VEVENT_CANCELLED);
						}
					}
					r_l.add(obj);
				}
				else{
					if(isBoss == 1){
						//All the common events in the local copy of boss and the remote copy will be either 
						//(CONFIRMED or CANCELLED by the boss) or  
						if(((VEvent)obj).getStatus() == Status.VTODO_NEEDS_ACTION){
							((VEvent)com).getProperties().remove(((VEvent)com).getStatus());
							((VEvent)com).getProperties().add(Status.VEVENT_CANCELLED);
						}
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
			myCalendar.getComponents(Component.VEVENT).addAll(r_l);
			
			
			
			if(isBoss == 1){
				System.out.println("\nEvents scheduled to happen over next one month: ");
				//filter will give next one month events
				ComponentList eventsInNextOneMonth = (ComponentList)filter.filter(myCalendar.getComponents(Component.VEVENT));
				
				//IndexedComponentList used to sort the events by Start date of the event
				IndexedComponentList eventsSortedByDate = new IndexedComponentList(eventsInNextOneMonth, Property.DTSTART);
				ComponentList eventsOnDate;
				
				//To iterate over from startDate to endDate
				//To print all the events in the next one month whose status is not cancelled sorted by date
				for (java.util.Date date = startDate.getTime(); !start.after(endDate.getTime()); startDate.add(java.util.Calendar.DATE, 1), date = startDate.getTime()) 
		        {
					//All the events on the date passed as argument returned as a list
					eventsOnDate = eventsSortedByDate.getComponents((new Date(date)).toString());
		            
					if(eventsOnDate.isEmpty() == false){
						for(Object e : eventsOnDate){
							//if the status of the event is not cancelled, print it and change its status to cancelled.
							if(((VEvent)e).getStatus() != Status.VEVENT_CANCELLED){
								System.out.println("\nstart: " + ((VEvent)e).getStartDate() + "  end: " + ((VEvent)e).getEndDate() + "\t" + ((VEvent)e).getSummary());
								((VEvent)e).getProperties().remove(((VEvent)e).getStatus());
								((VEvent)e).getProperties().add(Status.VEVENT_CANCELLED);
							}
			            }
					}
		        }
				
				//Now, all the events will be having status as cancelled.Now, we will ask the boss which events(out of those which are printed on screen) 
				//he/she wants to attend and the events which the boss selects will be marked as confirmed.
				
				//Indexing events by summary
				IndexedComponentList eventsSortedBySummary = new IndexedComponentList(eventsInNextOneMonth, Property.SUMMARY);
				String summary = new String();
				VEvent tempEvent;
				Scanner sc = new Scanner(System.in); 
				System.out.println("\nEnter the events you want to attend(enter \"exit\" to exit): ");
				while(true){
					summary = sc.nextLine();
					if(summary.equals("exit"))
						break;
					//tempEvent stores the event with description as value of summary
					tempEvent = (VEvent)(eventsSortedBySummary.getComponent(summary));
					tempEvent.getProperties().remove(tempEvent.getStatus());
					//changing the events status to confirmed
					tempEvent.getProperties().add(Status.VEVENT_CONFIRMED);
				}
				sc.close();
			}
			System.out.println("Generating file");
			//updating the local file from the myCalendar
			this.generateFile();
			
			System.out.println("File generated");
			//lock file before calculating time stamp because time stamp gets invalid if file is modified
			//after calculating time stamp.
			token = Connection.lock(lockFilePath);
			m2 = Connection.GetModifiedDate(remoteCalFilePath);
			//if file on server was not modified while sync, then put the consistent copy on the server 
			//else again sync with the modified file on server
			if(DateUtils.isSameDay(m1, m2)){
				Connection.putCalendar(FileName);
				Connection.unlock(lockFilePath, token);
				System.out.println("Sent back the calendar");
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
		
		
	}
	
	public void deleteDayEvent(int startTime, int endTime, int dayofmonth, int month, int year){
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
		@SuppressWarnings("deprecation")
		Filter filter = new Filter(new PeriodRule(period));

		Collection eventsToday = filter.filter(myCalendar.getComponents(Component.VEVENT));
		
		if(myCalendar.getComponents().containsAll(eventsToday)){
			myCalendar.getComponents().removeAll(eventsToday);
		}
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

		ComponentList eventsToday = (ComponentList)filter.filter(myCalendar.getComponents(Component.VEVENT));
		
		if(eventsToday.size() > 1){
			System.out.println("\nThere are multiple events in this period.");
			return;
		}
		else if(eventsToday.size() == 0){
			System.out.println("\nNo event in this period");
			return;
		}
		else{
			VEvent tempEvent = (VEvent)(eventsToday.get(0));
			if(tempEvent.getStatus() != Status.VEVENT_CANCELLED){
				tempEvent.getProperties().remove(tempEvent.getStatus());
				if(isBoss == 1){
					tempEvent.getProperties().add(Status.VEVENT_CANCELLED);
				}
				else{
					tempEvent.getProperties().add(Status.VTODO_NEEDS_ACTION);
				}
				if(isonline == 1){
					this.ResolveConflict();
				}
			}
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