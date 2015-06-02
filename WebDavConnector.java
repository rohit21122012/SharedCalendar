import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
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
		sardine = SardineFactory.begin("amit", "amit");
	}
	
	public Date GetModifiedDate(String url){
		List<DavResource> l = null;
		try {
			l = sardine.list(url);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Date d = l.get(0).getModified();
		return d;
	}
	
	public String lock(String url){
		String token = null;
		try {
			token = sardine.lock(url);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return token;
	}
	
	public void unlock(String url, String token){
		try {
			sardine.unlock(url,token);
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
				String token = sardine.lock(remoteCalFilePath);
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
				sardine.unlock(remoteCalFilePath,token);
			}
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
		Sardine sardine = SardineFactory.begin("amit", "amit");
		InputStream fis;
		try {
			fis = new FileInputStream(new File("mycalendar.ics"));
			sardine.put("http://localhost/webdav/mycalendar.ics",fis);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<DavResource> resources;
		try {
			resources = sardine.list("http://localhost/webdav/");
			for (DavResource res : resources)
			{
			     System.out.println(res); // calls the .toString() method.
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}