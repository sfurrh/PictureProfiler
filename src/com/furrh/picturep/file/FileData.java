package com.furrh.picturep.file;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FileData implements IFileData{
	protected Date modified;
	protected String name;
	protected String extension;
	protected String parentDirectory;
	protected long size;
	protected HashMap<String, String> exif;
	

	public FileData(File f){
		setName(f.getName());
		setParentDirectory(f.getParent());
		setLastModified(new Date(f.lastModified()));
		setSize(f.length());
		exif=new HashMap<String, String>();
	}

	public Date getLastModified() {
		return modified;
	}

	public void setLastModified(Date modified) {
		this.modified = modified;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		int io=name.lastIndexOf(".");
		if(io>0&&io<name.length()-1){
			setExtension(name.substring(io+1));
		}
	}

	public String getParentDirectory() {
		return parentDirectory;
	}

	public void setParentDirectory(String parentDirectory) {
		this.parentDirectory = parentDirectory;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public String getExtension() {
		return extension;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public HashMap<String, String> getExif() {
		return exif;
	}

	public void setExif(HashMap<String, String> exif) {
		this.exif = exif;
	}
	
	public void addExif(String key, String value){
		this.exif.put(key, value);
	}
	
	public String toString(){
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("\nFile: "+name);
		buffer.append("\n\tmodified: "+modified);
		buffer.append("\n\textension: "+extension);
		buffer.append("\n\tparentDirectory: "+parentDirectory);
		buffer.append("\n\tsize: "+size);
		for(Iterator<String> i=exif.keySet().iterator();i.hasNext();){
			String key=i.next();
			String value=exif.get(key);
			buffer.append("\n\t\t"+key+": "+value);
		}
		
		return buffer.toString();
	}


	@Override
	public Map<String, String> getProperties() {
		return getExif();
	}
	public Map<String, Object> getMap(){
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("name", name);
		map.put("modified", modified);
		map.put("extension", extension);
		map.put("parentDirectory", parentDirectory);
		map.put("size", new Long(size));
		if(getProperties().size()>0){
			map.put("properties", getProperties());
		}
		return map;
	}
	
}
