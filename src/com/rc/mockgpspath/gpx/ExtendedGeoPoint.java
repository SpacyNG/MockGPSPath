package com.rc.mockgpspath.gpx;

import com.google.android.maps.GeoPoint;

public class ExtendedGeoPoint extends GeoPoint {

	private final int hr;
	private final float elevation;
	private final long timestamp;

	public ExtendedGeoPoint(int latitudeE6, int longitudeE6, int hr,
			float elevation, long timestamp) {
		super(latitudeE6, longitudeE6);
		this.hr = hr;
		this.elevation = elevation;
		this.timestamp = timestamp;
	}

	public int getHr() {
		return hr;
	}

	public float getElevation() {
		return elevation;
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return "ExtendedGeoPoint[" + super.toString() + ", hr=" + hr
				+ ", elevation=" + elevation + ", timestamp=" + timestamp + "]";
	}
}
