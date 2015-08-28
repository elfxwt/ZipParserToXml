package com.exapmple.sophia_xu;

public class Main {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		ZipParser zp = new ZipParser();
		try {
			zp.getFileNameFromZip();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
