package com.furrh.picturep.file;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.GpsDirectory;

public class ExifFileData extends FileData {
	protected double latitude;
	protected double longitude;
	protected double altitude;
	boolean error=false;
	boolean haveLat=false;
	boolean haveLong=false;
	boolean haveAlt=false;

	public ExifFileData(File f){
		super(f);
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(f);
			Iterator<Directory> directories = metadata.getDirectories().iterator();
			while (directories.hasNext()) {
				Directory directory = directories.next();
				Iterator<Tag> tags = directory.getTags().iterator();
				while (tags.hasNext()) {
					Tag tag = tags.next();
					addExif(tag.getTagName(), tag.getDescription());					
				}
				if(directory.getName()=="GPS"){
					GpsDirectory gpsdir = (GpsDirectory) directory;
					Rational latpart[] = gpsdir.getRationalArray(GpsDirectory.TAG_GPS_LATITUDE);
					Rational lonpart[] = gpsdir.getRationalArray(GpsDirectory.TAG_GPS_LONGITUDE);
					String northing = gpsdir.getString(GpsDirectory.TAG_GPS_LATITUDE_REF);
					String easting = gpsdir.getString(GpsDirectory.TAG_GPS_LONGITUDE_REF);

					if(gpsdir.containsTag(GpsDirectory.TAG_GPS_ALTITUDE)){
						double alt = gpsdir.getDouble(GpsDirectory.TAG_GPS_ALTITUDE);
						setAltitude(alt);
					}

					double latsign = 1.0d; if (northing.equalsIgnoreCase("S")) latsign = -1.0d;
					double lonsign = 1.0d; if (easting.equalsIgnoreCase("W")) lonsign = -1.0d;
					double lat = (Math.abs(latpart[0].doubleValue()) + latpart[1].doubleValue()/60.0d + latpart[2].doubleValue()/3600.0d)*latsign;
					double lon = (Math.abs(lonpart[0].doubleValue()) + lonpart[1].doubleValue()/60.0d + lonpart[2].doubleValue()/3600.0d)*lonsign;

					if (Double.isNaN(lat) || Double.isNaN(lon)){
						error = true;
					}else{
						setLatitude(lat);
						setLongitude(lon);	
					}
				}
			}
		} catch (ImageProcessingException e) {
			System.out.println(e.getMessage()+" for file: "+f.getAbsolutePath());
		} catch (MetadataException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
		haveLat=true;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
		haveLong=true;
	}

	public double getAltitude() {
		return altitude;
	}

	public void setAltitude(double altitude) {
		this.altitude = altitude;
		haveAlt=true;
	}

	public String toString(){
		StringBuffer buffer = new StringBuffer(super.toString());
		buffer.append("\n\tlatitude: "+latitude);
		buffer.append("\n\tlongitude: "+longitude);
		buffer.append("\n\taltitude: "+altitude);

		return buffer.toString();
	}

	@Override
	public Map<String, Object> getMap() {
		Map<String,Object> map = super.getMap();
		if(haveLong){
			map.put("longitude", Double.toString(longitude));
		}
		if(haveLat){
			map.put("latitude", Double.toString(latitude));
		}
		if(haveAlt){
			map.put("altitude", Double.toString(altitude));
		}
		return map;
	}
	
}
