package com.furrh.picturep;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.furrh.picturep.file.ExifFileData;
import com.furrh.picturep.file.ImageFilenameFilter;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
public class PictureProfiler {
	String mongoHost="mongodb://localhost";
	String mongoDb="files";
	MongoClient client;
	DB db;
	boolean initialized=false;

	public static void main(String[] args){
		PictureProfiler pp=new PictureProfiler();
		String dir = "C:/Users/sean.furrh/Dropbox/Camera Uploads";
		//pp.profile(new File(dir));
		//GeoDataFile data = Geo.scan(new File(new File(dir),"IMG_0291.jpg"));
		//IFileData data = new ExifFileData(new File(new File(dir),"2014-02-28 19.10.52.jpg"));
		
		pp.profile(new File(dir));
	}
	public PictureProfiler(){
		if(initialize()){
			System.out.println("PictureProfiler initialized");
		}else{
			System.out.println("Errors encountered during initialization.  Stopping.");
		}
	}
	private boolean initialize(){
		try {
			client =  new MongoClient(new MongoClientURI(mongoHost));
			db = client.getDB(mongoDb);
			initialized=true;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return initialized;
	}
	public void profile(File dir){
		String[] files = dir.list(new ImageFilenameFilter());
		DBCollection collection = db.getCollection("pictures");
		if(files!=null){
			float total=files.length;
			int tenprcnt = files.length/10;
			for(int i=0;i<files.length;i++){
				File file = new File(dir,files[i]);
				String md5 = file.getAbsolutePath();				
				try {
					FileInputStream fis = new FileInputStream(file);
					md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				ExifFileData xfile = new ExifFileData(file);
				Map<String,Object> map = xfile.getMap();
				map.put("_id", md5);
				String pdir = (String) map.get("parentDirectory");
				String[] dirNames = pdir.split(File.separator.equals("/")?"/":"\\\\");				
				
				BasicDBObject dbObj = new BasicDBObject(map)
					.append("filenames",file.getAbsolutePath())
					.append("pathNodes",Arrays.asList(dirNames));

				collection.update(new BasicDBObject("_id",md5), dbObj, true, false);
				if(i>1 && i % tenprcnt == 0){
					double f = Math.ceil((i/total) * 100);
					System.out.println(f+"% done");
				}
			}
		}
	}
	void readFile(String fileName){		
		File file = new File( fileName );
		readFile(file);
	}
	void readFile(File file ) {
		try {            
			ImageInputStream iis = ImageIO.createImageInputStream(file);
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

			while (readers.hasNext()) {

				// pick the first available ImageReader
				ImageReader reader = readers.next();

				// attach source to the reader
				reader.setInput(iis, true);

				// read metadata of first image

				IIOMetadata metadata;
				metadata= reader.getImageMetadata(0);


				String[] names = metadata.getMetadataFormatNames();
				int length = names.length;
				for (int i = 0; i < length; i++) {
					System.out.println( "Format name: " + names[ i ] );
					displayMetadata(System.out,metadata.getAsTree(names[i]),0);
					
				}


			}
		}
		catch (Exception e) {

			e.printStackTrace();
		}
	}
	void indent(PrintStream out, int level) {
		for (int i = 0; i < level; i++){
			out.print("    ");
		}
	}
	void displayMetadata(PrintStream out, Node node, int level) {
		// print open tag of element
		indent(out, level);
		out.print("<" + node.getNodeName());
		NamedNodeMap map = node.getAttributes();
		if (map != null) {

			// print attribute values
			int length = map.getLength();
			for (int i = 0; i < length; i++) {
				Node attr = map.item(i);
				out.print(" " + attr.getNodeName() + "=\"" + attr.getNodeValue() + "\"");
			}
		}

		Node child = node.getFirstChild();
		if (child == null) {
			// no children, so close element and return
			System.out.println("/>");
			return;
		}

		// children, so close current tag
		out.println(">");
		while (child != null) {
			// print children recursively
			displayMetadata(out,child, level + 1);
			child = child.getNextSibling();
		}

		// print close tag of element
		indent(out,level);
		out.println("</" + node.getNodeName() + ">");
	}
	public void geo(String filename){
		File jpegFile = new File(filename);
		geo(jpegFile);
	}
	public void geo(File jpegFile) {


	}
}
