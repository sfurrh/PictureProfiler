package com.furrh.simplymongo;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.simpleframework.http.Cookie;
import org.simpleframework.http.Path;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;

public class SimplyMongoServer implements Container {
	static Logger log = Logger.getLogger(SimplyMongoServer.class.getName());
	private Cipher cipher=null;
	private Cipher decipher=null;
	private Key serverKey=null;
	static Server server;
	static boolean isRunning=false;
	static int mongoPort = 27017;
	static String mongoHost = "localhost";
	static MongoClient mongoClient;
	static DB fileDb;
	static DBCollection picturesCollection;
	int port=8080;
	Properties contentTypes;
	String prefabFileName = "prefab.xml";
	long prefabLastModified;
	File prefabFile;
	HashMap<String, Prefab> prefabs;
	public static void main(String[] args) throws Exception {
		if(log.getAllAppenders().hasMoreElements()){
			/**
			 * log4j is configured
			 */
		}else{
			ConsoleAppender console = new ConsoleAppender(); //create appender
			//configure the appender
			String PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%p] %C{2}\t%m%n";
			console.setLayout(new PatternLayout(PATTERN)); 
			console.setThreshold(Level.DEBUG);
			console.activateOptions();
			//add appender to any Logger (here is root)
			Logger.getRootLogger().addAppender(console);
		}

		SimplyMongoServer server = new SimplyMongoServer();
		if(args.length>0){
			if("stop".equalsIgnoreCase(args[0])){
				server.stop();
				return;
			}
		}
		server.start(8080);
	}

	public boolean start(int p)throws Exception{
		port=p;
		if(initialize()){
			mongoClient = new MongoClient( mongoHost , mongoPort );

			try {
				log.info("attempting to connect to mongo: "+mongoClient.getAddress());
				mongoClient.getConnector().getDBPortPool(mongoClient.getAddress()).get().ensureOpen();
				fileDb = mongoClient.getDB("files");
				picturesCollection = fileDb.getCollection("pictures");
			} catch (Exception e) {
				log.fatal("Unable to connect to Mongo: "+e.getMessage());
				return false;
			}

			server = new ContainerServer(this);
			Connection connection = new SocketConnection(server);
			SocketAddress address = new InetSocketAddress(port);

			isRunning = true;
			log.info("============Simply Starting on port "+port+"============");
			connection.connect(address);
		}else{
			log.fatal("============Simply failed to start============");
		}
		return true;
	}
	private boolean initialize(){
		contentTypes = new Properties();
		try {
			ClassLoader loader = SimplyMongoServer.class.getClassLoader();
			InputStream props = loader.getResourceAsStream("SimplyMongoContentTypes.prop");
			contentTypes.load(props);
			props.close();
			
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, getServerKey());

			decipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			decipher.init(Cipher.ENCRYPT_MODE, getServerKey());

		} catch (Exception e) {
			log.info("unable to load SimplyMongoContentTypes.prop because: "+e.getMessage());
			return false;
		}
		if(!contentTypes.containsKey("*")){
			contentTypes.put("*", "text/html");
		}

		return true;

	}

	/**
	 * I haven't really implemented this yet but my thought is that I could create session keys
	 * with a hash/cipher
	 * @return
	 * @throws Exception
	 */
	private Key getServerKey()throws Exception{
		if(serverKey==null){
			String computername=InetAddress.getLocalHost().getHostName();
			String passphrase = "horses like grass better";
			MessageDigest digest = null;
			digest=MessageDigest.getInstance("SHA");
			digest.update(passphrase.getBytes());
			serverKey = new SecretKeySpec(digest.digest(), 0, 16, "AES");
		}
		return serverKey;
	}
	private String encrypt(String in)throws Exception{
		byte[] ciphertext = cipher.doFinal(in.getBytes());
		return ciphertext.toString();		
	}
	private String decript(String in)throws Exception{
		String cleartext = new String(decipher.doFinal(in.getBytes()));
		return cleartext;
	}
	public void stop()throws Exception{
		log.info("============Simply Stopping on port "+port+"============");
		server.stop();
		log.info("============Closing Mongo Client============");
		mongoClient.close();
		isRunning = false;
		System.exit(0);
	}
	private Prefab getPrefab(String key){
		prefabFile = new File(prefabFileName);
	
		long currentLM = prefabFile.lastModified();
		if(prefabs==null || !prefabs.containsKey(key) || (prefabLastModified>0 && currentLM>prefabLastModified)){
			loadPrefabs(null);
		}
		return prefabs.get(key);
	}
	private void loadPrefabs(InputStream is){
		if(is==null){
			try {
				ClassLoader loader = SimplyMongoServer.class.getClassLoader();
				prefabFile = new File(prefabFileName);

				if(prefabFile.exists()){
					prefabLastModified = prefabFile.lastModified();
					is = new FileInputStream(prefabFile);
				}else{
					is = loader.getResourceAsStream("prefab.xml");
				}


			} catch (Exception e) {
				log.warn("Unable to load prefab.xml because: "+e.getMessage());
			}
		}
		try {

			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();

			DefaultHandler handler = new DefaultHandler() {

				Prefab currentPrefab;
				StringBuffer currentChars=null;


				@Override
				public void startDocument() throws SAXException {					
					super.startDocument();
					prefabs=new HashMap<String,Prefab>();
				}

				public void startElement(String uri, String localName,String qName, 
						Attributes attributes) throws SAXException {

					if(qName.equalsIgnoreCase("prefab")){
						String action = attributes.getValue("action");
						String name = attributes.getValue("name");
						currentPrefab=new Prefab(name,action,null);
						currentChars=new StringBuffer();
					}
				}

				public void endElement(String uri, String localName,
						String qName) throws SAXException {
					if(qName.equalsIgnoreCase("prefab")){
						currentPrefab.setValue(currentChars.toString());
						prefabs.put(currentPrefab.getName(), currentPrefab);
					}
				}

				public void characters(char ch[], int start, int length) throws SAXException {
					if(currentChars!=null){
						currentChars.append(ch,start,length);
					}				 
				}

			};
			if(is!=null){
				saxParser.parse(is, handler);
			}else{
				throw new Exception("prefab input stream is null");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			if(is!=null){
				try {
					is.close();
				} catch (Exception e) {
					log.error("error closing prefabStream",e);
				}
			}
		}
	}
	public void handle(Request request, Response response) {
		long time = System.currentTimeMillis();
		Cookie sessionCookie = request.getCookie("SSESSION");
		try {
			Path path = request.getPath();
			log.debug("path: "+path);
			String[] pathSegments = path.getSegments();
			String scope="file";
			if(pathSegments.length>0){
				scope=pathSegments[0];
			}
			if("db".equalsIgnoreCase(scope)){
				handleDBRequest(request,response);
			}else if("stop".equalsIgnoreCase(scope)){
				stop();
			}else if("thumbnail".equalsIgnoreCase(scope)){
				String id=pathSegments[1];
				byte[] thumb = handleThumbnailRequest(id);
				response.setValue("Content-Type", "image/jpeg");
				response.setValue("Server", "SimplyMongo/1.0 (Simple 4.0)");
				response.setDate("Date", time);
				response.setDate("Last-Modified", System.currentTimeMillis());
				response.setContentLength(thumb.length);
				response.getOutputStream().write(thumb);
			}else if("cmd".equalsIgnoreCase(scope)){
				OutputStream out = response.getOutputStream();
				String command = pathSegments[1];
				
				response.setValue("Content-Type", "application/json");
				response.setValue("Server", "SimplyMongo/1.0 (Simple 4.0)");
				response.setDate("Date", System.currentTimeMillis());
				response.setDate("Last-Modified", System.currentTimeMillis());
				out.write("{\"results\":[".getBytes());
				int ct=0;
				if("pictures.bydate.grouped".equalsIgnoreCase(command)){
					Prefab prefab = getPrefab("pictures.bydate.grouped");
					BasicDBList list = prefab.hasDbList?prefab.getDbList():(BasicDBList) JSON.parse(prefab.getValue());
					AggregationOutput result=null;
					if(list!=null && list.size()>1){
						DBObject first=(DBObject) list.remove(0);
						DBObject[] stages=null;
						stages = list.toArray(new BasicDBObject[]{});							
						result=picturesCollection.aggregate(first,stages);
					}else if (list !=null){
						DBObject first=(DBObject) list.remove(0);
						result=picturesCollection.aggregate(first);
					}
					if(result!=null){
						out.write(result.results().toString().getBytes());
					}
				}else if("pictures.bymonth".equalsIgnoreCase(command)){
					String year = pathSegments[2];
					String month = pathSegments[3];
					Calendar sd = Calendar.getInstance();
					sd.set(Calendar.YEAR, Integer.parseInt(year));
					sd.set(Calendar.MONTH, Integer.parseInt(month)-1);
					sd.set(Calendar.DATE, 1);
					sd.set(Calendar.HOUR, 0);
					sd.set(Calendar.MINUTE, 0);
					sd.set(Calendar.SECOND, 0);
					
					Calendar ed = Calendar.getInstance();
					ed.set(Calendar.YEAR, Integer.parseInt(year));
					ed.set(Calendar.MONTH, Integer.parseInt(month));
					ed.set(Calendar.DATE, 1);
					ed.set(Calendar.HOUR, 0);
					ed.set(Calendar.MINUTE, 0);
					ed.set(Calendar.SECOND, 0);
										
					BasicDBObject o = new BasicDBObject("modified",
							new BasicDBObject()
								.append("$gte",sd.getTime())
								.append("$lt",ed.getTime())
							);
					log.debug(o.toString());
					DBCursor cursor = picturesCollection.find(o);
					try {
						
						while(cursor.hasNext()) {
							if(ct++>0){
								out.write(",".getBytes());
							}
							BasicDBObject doc = (BasicDBObject) cursor.next();						       
							out.write(doc.toString().getBytes());			       
						}
					} finally {
						cursor.close();
					}
				}
				out.write(("],\"count\":"+ct+"}").toString().getBytes());
				out.flush();
				out.close();
			}else if("img".equalsIgnoreCase(scope)){
				String id=pathSegments[1];
				BasicDBObject o = new BasicDBObject("_id",id);
				DBObject imgObject = picturesCollection.findOne(o, new BasicDBObject("filenames",true));
				File imgFile = new File((String) imgObject.get("filenames"));
				if(imgFile.exists()){
					response.getOutputStream();
					new FileInputStream(imgFile).getChannel().transferTo(0, imgFile.length(), response.getByteChannel());
					response.close();
				}
			}else if("thumb".equalsIgnoreCase(scope)){
				String id=pathSegments[1];
				BasicDBObject o = new BasicDBObject("_id",id);
				DBObject imgObject = picturesCollection.findOne(o, new BasicDBObject("filenames",true)
					.append("properties.Thumbnail Offset","offset").append("properties.Thumbnail Length","length"));
				File imgFile = new File((String) imgObject.get("filenames"));
				log.debug("image: "+imgObject);
				DBObject properties = (DBObject) imgObject.get("properties");
				String offset = (String) properties.get("Thumbnail Offset");
				String length = (String) properties.get("Thumbnail Length");
				int io=offset.indexOf(" ");
				if(io>0){
					offset=offset.substring(0,io);
				}
				io=length.indexOf(" ");
				if(io>0){
					length=length.substring(0,io);
				}
				log.debug("thumb offset="+offset+"; thumb length="+length);
				
				if(imgFile.exists()){					
					FileInputStream is = new FileInputStream(imgFile);
					int off = Integer.parseInt(offset);
					is.skip(off);
					int len = Integer.parseInt(length);
					byte[] bytes=new byte[len];
					io=is.read(bytes, 0, len);
					
					/**
					 * i don't know why i have to do this but the start of the jpg isn't 
					 * really at the offset specified by the exif data.  i read somewhere
					 * that the first two bytes of a jpg are FFD8 so i look in the byte
					 * array for the first occurrence of that sequence and that is 
					 * where the thumbnail jpg actually starts.
					 */
					byte FF=(byte) 255;
					byte D8=(byte) 216;					
					int start=0;
					
					for(int i=0;i<bytes.length;i++){
						byte b=bytes[i];						
						if(b==FF && bytes[i+1]==D8){
							start=i;
							break;
						}
					}
					log.debug("start of thumbnail is at "+start+" from offset");
					response.getOutputStream().write(bytes, start, len-start);					
					response.close();
				}
			}else{
				String pathString ="."+path.getPath(); 

				File f=new File(pathString);
				if(f.exists()&& f.canRead()){
					if(f.isDirectory()){
						File index = new File(f,"index.html");
						if(index.exists()){
							f=index;
						}
					}
					if(f.isFile()){
						String fileName = f.getName();
						String ext = "";
						int io=fileName.lastIndexOf(".")+1;
						if(io>0 && io!=fileName.length()){
							ext = fileName.substring(io);
						}
						/**
						 * default to text/html content type if none is matching and 
						 * no wildcard is specified
						 */
						String contentType = contentTypes.getProperty(ext,contentTypes.getProperty("*", "text/html"));

						response.setValue("Content-Type", contentType);
						response.setValue("Server", "SimplyMongo/1.0 (Simple 4.0)");
						response.setDate("Date", time);
						response.setDate("Last-Modified", f.lastModified());

						response.getOutputStream();
						new FileInputStream(f).getChannel().transferTo(0, f.length(), response.getByteChannel());
						response.close();
					}else{
						response.setValue("Content-Type", "text/plain");
						response.setValue("Server", "SimplyMongo/1.0 (Simple 4.0)");
						response.setDate("Date", time);
						response.setDate("Last-Modified", time);
						PrintStream body = response.getPrintStream();
						body.println("Invalid Request: "+path.getPath());
						body.close();
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}finally{
			if(!response.isCommitted()){
				try {
					response.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	} 

	public byte[] handleThumbnailRequest(String id){
		try {
			DB db = mongoClient.getDB( "files" );
			DBCollection coll = db.getCollection("pictures");
			BasicDBObject o = new BasicDBObject("_id",id);
			DBObject result = coll.findOne(o);
			Map rmap = result.toMap();
			Map properties = (Map) rmap.get("properties");
			if(properties!=null){
				String offset = (String) properties.get("Thumbnail Offset");
				
				int io=offset.indexOf(" ");
				if(io>0){
					offset=offset.substring(0,io);
				}
				int off = Integer.parseInt(offset);
				String length = (String) properties.get("Thumbnail Length");
				io=length.indexOf(" ");
				if(io>0){
					length=length.substring(0,io);
				}
				int l = Integer.parseInt(length);
				
				String fileName = (String) rmap.get("filenames");
				System.out.println("fileName: "+fileName);
				File file = new File(fileName);
				if(file.exists()){
					FileInputStream fis = new FileInputStream(file);
					System.out.println("offset="+off+" l="+l);
					byte[] bytes = new byte[l];
					fis.skip(off-1);
					fis.read(bytes, 0, l);
					fis.close();
					return bytes;
				}
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	public void handleDBRequest(Request request, Response response)throws Exception {
		Path path = request.getPath();
		String[] pathSegments = path.getSegments();
		if(pathSegments.length>1){
			String dbName = pathSegments[1];
			DB db = mongoClient.getDB( dbName );
			if(pathSegments.length>2){
				String collName = pathSegments[2];
				DBCollection coll = db.getCollection( collName );
				String action = "find";
				if(pathSegments.length>3){
					action = pathSegments[3];
				}
				response.setValue("Content-Type", "application/json");
				response.setValue("Server", "SimplyMongo/1.0 (Simple 4.0)");
				response.setDate("Date", System.currentTimeMillis());
				response.setDate("Last-Modified", System.currentTimeMillis());

				OutputStream out = response.getOutputStream();
				//PrintStream body = response.getPrintStream();
				//body.println("{results:[");
				out.write("{\"results\":[".getBytes());
				int ct=0;
				try {
					String q = request.getParameter("q");
					if(q==null){
						q="";
					}
					String o = request.getParameter("o");
					String max = request.getParameter("max");

					int m=1000;
					try{
						m=Integer.parseInt(max);
					}catch(NumberFormatException nfe){
						log.debug("Unable to parse max value: '"+max+"'.  Using the default "+m);
					}
					DBObject query = (DBObject)JSON.parse(q);

					log.info(dbName+"/"+collName+"/"+action + " query: "+query+"; object: "+o);
					if("find".equalsIgnoreCase(action)){
						DBCursor cursor = coll.find(query).limit(m);
						String sort = request.getParameter("sort");
						if(sort!=null){
							DBObject orderBy= (DBObject) JSON.parse(sort);
							cursor.sort(orderBy);
						}

						try {
							while(cursor.hasNext()) {
								if(ct++>0){
									out.write(",".getBytes());
								}
								BasicDBObject doc = (BasicDBObject) cursor.next();						       
								out.write(doc.toString().getBytes());			       
							}
						} finally {
							cursor.close();
						}
						db.cleanCursors(true);
					}else if("insert".equalsIgnoreCase(action)){
						WriteResult result = coll.insert(query);
						out.write(result.toString().getBytes());
					}else if("update".equalsIgnoreCase(action)){
						DBObject newObject = (DBObject)JSON.parse(o);
						WriteResult result = coll.update(query,newObject
								,false
								,"true".equalsIgnoreCase(request.getParameter("multi"))
						);
						out.write(result.toString().getBytes());
					}else if("upsert".equalsIgnoreCase(action)){
						DBObject newObject = (DBObject)JSON.parse(o);
						WriteResult result = coll.update(query,newObject
								,true
								,"true".equalsIgnoreCase(request.getParameter("multi"))
						);
						out.write(result.toString().getBytes());
					}else if("save".equalsIgnoreCase(action)){
						WriteResult result = coll.save(query);
						out.write(result.toString().getBytes());
					}else if("remove".equalsIgnoreCase(action)){
						WriteResult result = coll.remove(query);
						out.write(result.toString().getBytes());
					}else if("aggregate".equalsIgnoreCase(action)){
						BasicDBList list = (BasicDBList) JSON.parse(o);
						AggregationOutput result=null;
						if(list!=null && list.size()>1){
							DBObject first=(DBObject) list.remove(0);
							DBObject[] stages=null;
							stages = list.toArray(new BasicDBObject[]{});							
							result=coll.aggregate(first,stages);
						}else if (list !=null){
							DBObject first=(DBObject) list.remove(0);
							result=coll.aggregate(first);
						}
						if(result!=null){
							out.write(result.results().toString().getBytes());
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}finally{
					out.write(("],\"count\":"+ct+"}").toString().getBytes());
					out.flush();
					out.close();
				}

			}else{
				Set<String> collNames = db.getCollectionNames();
				try {
					String json = JSON.serialize(collNames);
					response.getPrintStream().println(json);
					response.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}else{
			List<String> dbNames = mongoClient.getDatabaseNames();
			try {
				String json = JSON.serialize(dbNames);
				response.getPrintStream().println(json);
				log.debug("json: "+json);
				response.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
class Prefab{
	private String action;
	private String name;
	private String value;
	private BasicDBList dbList;
	boolean hasDbList = false;
	public Prefab(String n, String a, String v){
		this.action=a;
		this.name=n;
		this.value=v;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public BasicDBList getDbList() {
		return dbList;
	}
	public void setDbList(BasicDBList dbList) {
		this.dbList = dbList;
		if(dbList!=null){
			hasDbList=true;
		}
	}
	
}