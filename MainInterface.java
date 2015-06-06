/*
 * Program : This is a shared calendar program which allows sharing of a calendar 
 * 			 between a boss and his/her secretaries using WebDav protocol
 * Libraries used : Sardine (Webdav Client), Apache Webdav server, iCal4j (Writing iCalendar format i.e. .ics calendar files)
 * 					
 * About the java class : This is the main interface for the calendar application
 * 
 * Programmers : Rohit Patiyal and Amit Yadav
 * 
 * */
import java.util.Scanner;

//Class MainInterface which provides the interface to the users for interacting with the calendar
public class MainInterface {
	/*
	 * This class is the main interface class for the calendar
	 * */
	private static Scanner sc; 
	private static ICSCalendar myFile;
	
	private static void init(){
		sc = new Scanner(System.in);
		System.out.println("Welcome to Shared Calendar");
		String localFile = "finaltesting.ics";
		myFile = new ICSCalendar(localFile);
		System.out.println("\nAre you boss/secretary(boss:1, secretary:0): ");
		myFile.SetBoss(sc.nextInt());
	}
	
	private static int prompt(){
		System.out.println("Press 0 to exit");
		System.out.println("Press 1 to see your calendar");
		System.out.println("Press 2 to add an Event");
		System.out.println("Press 3 to cancel Event(s)");
		System.out.println("Press 4 to toggle between offline and online");
		return sc.nextInt();
	}

	public static void main(String[] args){
		
		init();
		int choice = prompt();
		while (true){
			performChoice(choice);
			if(choice == 0)
				break;
			choice = prompt();			
		}
		
	}

	private static void performChoice(int choice) {
		int online;
		int dayOfMonth, month, year;
		int startTime, endTime; 
		int startDay, startMonth, startYear, endDay, endMonth, endYear;
		String summary;
		switch (choice) {
		case 0:
			System.out.print("Exiting...");
			myFile.generateFile();
			System.out.println("Done");
			break;
		case 1:
			System.out.println("Enter the startdate and enddate to see list of events");
			System.out.println("Hint: 1 6 2015 31 12 2015");
			System.out.print(">");
			startDay = sc.nextInt();
			startMonth = sc.nextInt();
			startYear = sc.nextInt();
			endDay = sc.nextInt();
			endMonth = sc.nextInt();
			endYear = sc.nextInt();
			//myFile.seeMonthlyEvents(month, year);
			myFile.seeEventsBetween(startDay, startMonth,  startYear, endDay, endMonth, endYear);
			break;
		case 2:
			System.out.println("Enter date, month, year, and summary to add an event");
			System.out.println("Hint: 1 1 2016 New Year!!! ");
			System.out.print(">");
			dayOfMonth = sc.nextInt();
			month = sc.nextInt();
			year = sc.nextInt();
			summary = sc.nextLine();
			System.out.println("Enter Start time (hr) and End time (hr) for the event");
			System.out.println("Hint: 8 16");
			System.out.print(">");
			startTime = sc.nextInt();
			endTime = sc.nextInt();
			//description = sc.next();
			myFile.addDayEvent(startTime, endTime, dayOfMonth, month, year, summary);
			System.out.println("Event added for " + startTime + ":00-" + endTime + ":00  "  + dayOfMonth + "/" + month + "/" + year + ": " + summary);
			break;
		case 3:
			System.out.println("Enter date, month, and year to delete an event");
			dayOfMonth = sc.nextInt();
			month = sc.nextInt();
			year = sc.nextInt();
			System.out.println("Enter Start time (hr) and End time (hr) for the event");
			startTime = sc.nextInt();
			endTime = sc.nextInt();
			myFile.CancelEvent(startTime, endTime, dayOfMonth, month, year);
			System.out.println("Event(s) cancelled for " + startTime + ":00-" + endTime + ":00  "  + dayOfMonth + "/" + month + "/" + year);
			break;
		case 4:
			System.out.println("Press 1 to go online");
			System.out.print(">");
			online = sc.nextInt();
			if(online == 1){
				myFile.SetOnline(1);
				System.out.println("Calendar Online now");
			}else{		
				myFile.SetOnline(0);
				System.out.println("Calendar offline now");
			}
			break;
		default:
			System.out.println("Please enter a valid choice");
			break;
		}
	}
}
