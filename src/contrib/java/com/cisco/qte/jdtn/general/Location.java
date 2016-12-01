/**
Copyright (c) 2010, Cisco Systems, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

    * Neither the name of the Cisco Systems, Inc. nor the names of its
    contributors may be used to endorse or promote products derived from this
    software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.cisco.qte.jdtn.general;

import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Description of the location and velocity of a node
 */
public class Location {
	/** Latitude, degrees, -180..180 */
	private double _latitude = 0.0d;
	/** Longitude, degrees, -90..90 */
	private double _longitude = 0.0d;
	/** Altitude, meters */
	private double _altitude = 0.0d;
	/** Speed, meters/sec */
	private double _speed = 0.0d;
	/** Direction, degrees, -180..180 relative to due north */
	private double _direction = 0.0d;
	
	public Location() {
		// Nothing
	}
	
	public Location(double latitude, double longitude) {
		setLatitude(latitude);
		setLongitude(longitude);
	}
	
	public Location(double latitude, double longitude, double altitude) {
		setLatitude(latitude);
		setLongitude(longitude);
		setAltitude(altitude);
	}
	
	public static Location xmlDecode(XmlRDParser xRdr)
	throws NumberFormatException, XmlRdParserException {
		Double latitude = parseDoubleAttr(xRdr, "lat");
		Double longitude = parseDoubleAttr(xRdr, "lon");
		if (latitude == null || longitude == null) {
			return null;
		}
		Location location = new Location(latitude, longitude);

		Double attrVal = parseDoubleAttr(xRdr, "alt");
		if (attrVal != null) {
			location.setAltitude(attrVal);
		}
		attrVal = parseDoubleAttr(xRdr, "speed");
		if (attrVal != null) {
			location.setSpeed(attrVal);
		}
		attrVal = parseDoubleAttr(xRdr, "direction");
		if (attrVal != null) {
			location.setDirection(attrVal);
		}
		return location;
	}
	
	private static Double parseDoubleAttr(XmlRDParser xRdr, String attrName)
	throws NumberFormatException, XmlRdParserException {
		Double result = null;
		String val = xRdr.getAttributeValue("lat");
		if (val != null && val.length() > 0) {
			result = new Double(val);
		}
		return result;
	}
	
	/** Latitude, degrees, -180..180 */
	public double getLatitude() {
		return _latitude;
	}
	/** Latitude, degrees, -180..180 */
	public void setLatitude(double latitude) {
		this._latitude = latitude;
	}
	/** Longitude, degrees, -90..90 */
	public double getLongitude() {
		return _longitude;
	}
	/** Longitude, degrees, -90..90 */
	public void setLongitude(double longitude) {
		this._longitude = longitude;
	}
	/** Altitude, meters */
	public double getAltitude() {
		return _altitude;
	}
	/** Altitude, meters */
	public void setAltitude(double altitude) {
		this._altitude = altitude;
	}
	/** Speed, meters/sec */
	public double getSpeed() {
		return _speed;
	}
	/** Speed, meters/sec */
	public void setSpeed(double speed) {
		this._speed = speed;
	}
	/** Direction, degrees, -180..180 relative to due north */
	public double getDirection() {
		return _direction;
	}
	/** Direction, degrees, -180..180 relative to due north */
	public void setDirection(double direction) {
		this._direction = direction;
	}
	
}
