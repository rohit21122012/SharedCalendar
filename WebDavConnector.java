import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

//Class WebDavConnector provides functionality for connecting to the WebDav server through WebDav protocol 
public class WebDavConnector {
	Sardine sardine ;
	
	private String remoteHome = "http://localhost/webdav/";
	private String localHome = "";
	
	public WebDavConnector() {
		sardine = SardineFactory.begin("rohit", "rohit");
	}
	
	public Date GetModifiedDate(String url){
//		Sardine sardine = SardineFactory.begin("rohit", "rohit");
		List<DavResource> l = null;
		try {
			l = sardine.list(url);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Date d = l.get(0).getModified();
		System.out.println("date is " + d);
		return d;
	}
	
	public String lock(String url){
		String token = null;
		try {
			System.out.println("Lock url is " + url);
			token = sardine.lock(url);
			System.err.println(url + " locked with token : " + token);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return token;
	}
	
	public void delete(String url){
		try {
			sardine.delete(url);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void unlock(String url, String token){
		try {
			sardine.unlock(url,token);
			System.err.println(url + " unlocked with token : " + token);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public boolean exists(String url){
		boolean b = false;
		try {
			b = sardine.exists(url);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
		}
		return b;
	}
	
	public void getCalendar(String localFile){
		String remoteCalFilePath = remoteHome + localFile;
		try {
			if(sardine.exists(remoteCalFilePath)){
				//String token = sardine.lock(remoteCalFilePath);
				InputStream is = sardine.get(remoteCalFilePath);
				
				//Write to a file
				File targetFile = new File(localFile);
				OutputStream os = new FileOutputStream(targetFile);
				byte[] buffer = new byte[8*1024];
				int bytesRead;
				while((bytesRead = is.read(buffer)) != -1){
					os.write(buffer,0,bytesRead);		
				}
				
				os.close();
				is.close();
				System.out.println("Remote file written over local file");
				//sardine.unlock(remoteCalFilePath,token);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void putLockFile(String lockFilePath){
		try {
			
			sardine.createDirectory(lockFilePath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void putCalendar(String localFile){
		String remoteCalFilePath = remoteHome + localFile;
		InputStream fis;
		try {
			fis = new FileInputStream(new File(localFile));
			sardine.put(remoteCalFilePath, fis);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		System.out.println("hello");
		System.out.println(System.getProperty("java.class.path"));
		Sardine sardine = SardineFactory.begin("rohit", "rohit");
//		WebDavConnector wb = new WebDavConnector();
		
		InputStream fis;
		try {
			fis = new FileInputStream(new File("mycalendar.ics"));
//			String token = sardine.lock("http://localhost/webdav/");
//			sardine.unlock("http://localhost/webdav/", token);
//			wb.putCalendar("mycalendar.ics");
			sardine.delete("http://localhost/webdav/mycalendar.ics");
//			System.out.println(wb.GetModifiedDate("http://localhost/webdav/mycalendar.ics"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		List<DavResource> resources;
//		try {
////			resources = sardine.list("http://localhost/webdav/");
//			for (DavResource res : resources)
//			{
//				System.out.println(res.getCustomProps());
//			     System.out.println(res); // calls the .toString() method.
//			}
//
//		}catch(Exception e){
//			e.printStackTrace();
//		}
	}
}