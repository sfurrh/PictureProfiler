package com.furrh.picturep.file;

import java.util.Date;
import java.util.Map;

public interface IFileData {
	public String getName();
	public String getParentDirectory();
	public Date getLastModified();
	public long getSize();
	public Map<String,String> getProperties();
	public Map<String,Object> getMap();
}
