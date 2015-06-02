
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;

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
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.IndexedComponentList;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.util.UidGenerator;

	
public class ICSCalendar {
	private static int eventCount = 0;
	private String FileName;
	private Calendar myCalendar;
	private int isonline = 0;
	private WebDavConnector Connection;
	
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			myCalendar = new Calendar();
			myCalendar.getProperties().add(new ProdId("-//IITMandi //Calendar using iCal4j 1.0//EN"));
			myCalendar.getProperties().add(net.fortuna.ical4j.model.property.Version.VERSION_2_0);
			myCalendar.getProperties().add(CalScale.GREGORIAN);
		}	
	}
	
	//sets online status
	public void SetOnline(int i){    
		isonline = i;
	}
	
	public void printCalendar() {
		System.out.println(myCalendar);
	}
	
	//ResolveConflict should be called only when online
	public void ResolveConflict(){
		String remoteCalFilePath = "http://localhost/webdav/" + FileName;
		java.util.Date m1,m2;
		String token;
		
		//locking the folder because if we do not put lock,
		//Problem: between exists check and putCalendar if somebody else calls putCalendar.
		token = Connection.lock("http://localhost/webdav");
		if(Connection.exists(remoteCalFilePath) == false){
			Connection.putCalendar(FileName);
		}
		Connection.unlock("http://localhost/webdav",token);
		
		//sync
		while(true){
			
			//locking the file so as to keep the file and its time stamp consistent
			token = Connection.lock(remoteCalFilePath);
			Connection.getCalendar(FileName);
			//time stamp when getting file from server
			m1 = Connection.GetModifiedDate(remoteCalFilePath);
			Connection.unlock(remoteCalFilePath,token);
			
			//code to make an Calendar object from the file stream obtained from server
			FileInputStream fin = null;
			try {
				fin = new FileInputStream(FileName);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			CalendarBuilder builder = new CalendarBuilder();
			Calendar remoteCalendar = null;
			try {
				remoteCalendar = builder.build(fin);
			} catch (IOException | ParserException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//to resolve conflict between calendar  and remoteCalendar : 
			//1. Update the status of events common in local and server. 
			//2. Add the events not present in local but present in server.
			
			ComponentList l = myCalendar.getComponents(Component.VEVENT);
			ComponentList r = remoteCalendar.getComponents(Component.VEVENT);
			
			//IndexedComponentList gives ComponentList indexed with the property mentioned,
			// in our case UID.
			IndexedComponentList inl = new IndexedComponentList(l, Property.UID);
			
			//r_l will store the events present in r but not in l
			ComponentList r_l = new ComponentList();
			
			for (Object obj : r){
				Uid tempUid = ((VEvent)obj).getUid();
				Component com = inl.getComponent(tempUid.getValue());
				if(com == null){
					r_l.add(com);
				}
				else{
					if(((VEvent)com).getStatus() != ((VEvent)obj).getStatus() && ((VEvent)com).getStatus() != Status.VTODO_NEEDS_ACTION){
						((VEvent)com).getProperties().remove(((VEvent)com).getStatus());
						((VEvent)com).getProperties().add(((VEvent)obj).getStatus());
					}
				}
			}
			myCalendar.getComponents(Component.VEVENT).addAll(r_l);
			
			//updating the local file from the myCalendar
			this.generateFile();
			
			//lock file before calculating time stamp because time stamp gets invalid if file is modified
			//after calculating time stamp.
			token = Connection.lock(remoteCalFilePath);
			m2 = Connection.GetModifiedDate(remoteCalFilePath);
			//if file on server was not modified while sync, then put the consistent copy on the server 
			//else again sync with the modified file on server
			if(m1 == m2){
				Connection.putCalendar(FileName);
				Connection.unlock(remoteCalFilePath,token);
				break;
			}
			else{
				Connection.unlock(remoteCalFilePath,token);
			}
		}
		//end of while
	}
	
	public void addDayEvent(int startTime, int endTime, int dayofmonth, int month, int year, String description){
		
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		//Adding the event to the core object
		myCalendar.getComponents().add(event);
		eventCount += 1;
		
		this.generateFile();
		
		if(isonline == 1){
			this.ResolveConflict();
		}
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
		Filter filter = new Filter(new PeriodRule(period));

		Collection eventsToday = filter.filter(myCalendar.getComponents(Component.VEVENT));
		//IndexedComponentList in = new IndexedComponentList(myCalendar.getComponents(Component.VEVENT), );
		
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
				tempEvent.getProperties().add(Status.VTODO_NEEDS_ACTION);
				if(isonline == 1){
					this.ResolveConflict();
				}
			}
		}
	}
	
	public void seeEventsBetween(int startDay, int endDay, int month, int year){
		java.util.Calendar startDate = new GregorianCalendar();
		startDate.set(java.util.Calendar.MONTH, month-1);
		startDate.set(java.util.Calendar.DAY_OF_MONTH, startDay);
		startDate.set(java.util.Calendar.YEAR, year);
		startDate.set(java.util.Calendar.HOUR_OF_DAY, 0);
		startDate.set(java.util.Calendar.MINUTE, 0);
		startDate.set(java.util.Calendar.SECOND, 0);

		java.util.Calendar endDate = new GregorianCalendar();
		endDate.set(java.util.Calendar.MONTH, month-1);
		endDate.set(java.util.Calendar.DAY_OF_MONTH, endDay);
		endDate.set(java.util.Calendar.YEAR, year);
		endDate.set(java.util.Calendar.HOUR_OF_DAY, 0);
		endDate.set(java.util.Calendar.MINUTE, 0);	
		endDate.set(java.util.Calendar.SECOND, 0);
		
		DateTime start = new DateTime(startDate.getTime());
		DateTime end = new DateTime(endDate.getTime());
		
		Period period = new Period(start, end);
		Filter filter = new Filter(new PeriodRule(period));

		ComponentList eventsBetween = (ComponentList)filter.filter(myCalendar.getComponents(Component.VEVENT));
		System.out.println("\nEvents between this period: ");
		for (Object object : eventsBetween) {
			System.out.println(object);
		}
	}

	public void generateFile(){
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