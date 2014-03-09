package com.furrh.picturep.file;

import java.io.File;
import java.io.FilenameFilter;

public class ImageFilenameFilter implements FilenameFilter {
	public static String[] imageExtensions=new String[]{"jpg","jpeg","bmp","gif","png"};
	@Override
	public boolean accept(File dir, String name) {
		int io = name.lastIndexOf(".");
		if(io>0 && io<name.length()-1){
			String ext = name.substring(io+1).toLowerCase();
			for(int i=0;i<imageExtensions.length;i++){
				if(ext.equals(imageExtensions[i])){
					return true;
				}
			}
		}
		return false;
	}

}
