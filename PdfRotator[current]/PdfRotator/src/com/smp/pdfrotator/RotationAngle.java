package com.smp.pdfrotator;

enum RotationAngle
{
	CW(90),
	CCW(270),
	CW180(180);

	private RotationAngle(int angle)
	{
		this.angle = angle;
	}

	@Override
	public String toString()
	{
		switch (this)
		{
			case CW:
				return "90" + deg + " CW";
			case CCW:
				return "90" + deg + " CCW";
			case CW180:
				return "180" + deg;
		}
		return "";
	}

	int angle;

	final static String deg = "\u00B0";

}
