package com.exapmple.sophia_xu;




import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Vector;

public class ZipParser {
	
	 static final int kEOCDSignature = 0x06054b50;
	    static final int kEOCDLen = 22;
	    static final int kEOCDNumEntries = 8; // offset to #of entries in file
	    static final int kEOCDSize = 12; // size of the central directory
	    static final int kEOCDFileOffset = 16; // offset to central directory

	    static final int kMaxCommentLen = 65535; // longest possible in ushort
	    static final int kMaxEOCDSearch = (kMaxCommentLen + kEOCDLen);

	    static final int kLFHSignature = 0x04034b50;
	    static final int kLFHLen = 30; // excluding variable-len fields
	    static final int kLFHNameLen = 26; // offset to filename length
	    static final int kLFHExtraLen = 28; // offset to extra length

	    static final int kCDESignature = 0x02014b50;
	    static final int kCDELen = 46; // excluding variable-len fields
	    static final int kCDEMethod = 10; // offset to compression method
	    static final int kCDEModWhen = 12; // offset to modification timestamp
	    static final int kCDECRC = 16; // offset to entry CRC
	    static final int kCDECompLen = 20; // offset to compressed length
	    static final int kCDEUncompLen = 24; // offset to uncompressed length
	    static final int kCDENameLen = 28; // offset to filename length
	    static final int kCDEExtraLen = 30; // offset to extra length
	    static final int kCDECommentLen = 32; // offset to comment length
	    static final int kCDELocalOffset = 42; // offset to local hdr

	    static final int kCompressStored = 0; // no compression
	    static final int kCompressDeflated = 8; // standard deflate

	    static final String LOG_TAG = "sophia";
	    static final boolean LOGV = true;

	    private ArrayList<String> fileNameList;

	    // 4-byte number

	    
	   public ZipParser(){
	          
	   }



	    static private int swapEndian(int i)
	    {
	        return ((i & 0xff) << 24) + ((i & 0xff00) << 8) + ((i & 0xff0000) >>> 8)
	                + ((i >>> 24) & 0xff);
	    }

	    public void getFileNameFromZip() throws Exception{
	        for(int id = 0;id < ConstValue.Watchface_GA_Name.length;id++){
	            String name = ConstValue.Watchface_GA_Name[id].replace("รถ","o")+".zip";
	            String zipFileName = ("E:\\zipParser\\"+ name);
	            File file = new File(zipFileName);

	            
	            fileNameList = new ArrayList<>();
	            if(file.exists()){
	             System.out.println("zipFileName:" + zipFileName);
	               getFileNameList(zipFileName);
	               createXMLFile(ConstValue.Watchface_GA_Name[id],fileNameList);
	            }
	        }


	    }


	    public void getFileNameList(String zipFileName) throws IOException{

	        File file = new File(zipFileName);
	        RandomAccessFile f = new RandomAccessFile(file, "r");
	        long fileLength = f.length();

	        if (fileLength < kEOCDLen) {
	            throw new IOException();
	        }

	        long readAmount = kMaxEOCDSearch;
	        if (readAmount > fileLength)
	            readAmount = fileLength;

	        /*
	         * Make sure this is a Zip archive.
	         */
	        f.seek(0);

	        int header = read4LE(f);
	        if (header == kEOCDSignature) {
	           System.out.println("Found Zip archive, but it looks empty");
	            throw new IOException();
	        } else if (header != kLFHSignature) {
	        	  System.out.println( "Not a Zip archive");
	            throw new IOException();
	        }

	        /*
	         * Perform the traditional EOCD snipe hunt. We're searching for the End
	         * of Central Directory magic number, which appears at the start of the
	         * EOCD block. It's followed by 18 bytes of EOCD stuff and up to 64KB of
	         * archive comment. We need to read the last part of the file into a
	         * buffer, dig through it to find the magic number, parse some values
	         * out, and use those to determine the extent of the CD. We start by
	         * pulling in the last part of the file.
	         */
	        long searchStart = fileLength - readAmount;

	        f.seek(searchStart);
	        ByteBuffer bbuf = ByteBuffer.allocate((int) readAmount);
	        byte[] buffer = bbuf.array();
	        f.readFully(buffer);
	        bbuf.order(ByteOrder.LITTLE_ENDIAN);

	        /*
	         * Scan backward for the EOCD magic. In an archive without a trailing
	         * comment, we'll find it on the first try. (We may want to consider
	         * doing an initial minimal read; if we don't find it, retry with a
	         * second read as above.)
	         */

	        // EOCD == 0x50, 0x4b, 0x05, 0x06
	        int eocdIdx;
	        for (eocdIdx = buffer.length - kEOCDLen; eocdIdx >= 0; eocdIdx--) {
	            if (buffer[eocdIdx] == 0x50 && bbuf.getInt(eocdIdx) == kEOCDSignature)
	            {
	                if (LOGV) {
	                	  System.out.println( "+++ Found EOCD at index: " + eocdIdx);
	                }
	                break;
	            }
	        }

	        if (eocdIdx < 0) {
	        	  System.out.println("Zip: EOCD not found, " + zipFileName + " is not zip");
	        }

	        /*
	         * Grab the CD offset and size, and the number of entries in the
	         * archive. After that, we can release our EOCD hunt buffer.
	         */

	        int numEntries = bbuf.getShort(eocdIdx + kEOCDNumEntries);
	        long dirSize = bbuf.getInt(eocdIdx + kEOCDSize) & 0xffffffffL;
	        long dirOffset = bbuf.getInt(eocdIdx + kEOCDFileOffset) & 0xffffffffL;

	        // Verify that they look reasonable.
	        if (dirOffset + dirSize > fileLength) {
	        	  System.out.println("bad offsets (dir " + dirOffset + ", size " + dirSize + ", eocd "
	                    + eocdIdx + ")");
	            throw new IOException();
	        }
	        if (numEntries == 0) {
	        	  System.out.println("empty archive?");
	            throw new IOException();
	        }

	        if (LOGV) {
	        	  System.out.println("+++ numEntries=" + numEntries + " dirSize=" + dirSize + " dirOffset="
	                    + dirOffset);
	        }

	        MappedByteBuffer directoryMap = f.getChannel()
	                .map(FileChannel.MapMode.READ_ONLY, dirOffset, dirSize);
	        directoryMap.order(ByteOrder.LITTLE_ENDIAN);

	        byte[] tempBuf = new byte[0xffff];

	        /*
	         * Walk through the central directory, adding entries to the hash table.
	         */

	        int currentOffset = 0;

	        /*
	         * Allocate the local directory information
	         */
	        ByteBuffer buf = ByteBuffer.allocate(kLFHLen);
	        buf.order(ByteOrder.LITTLE_ENDIAN);

	        for (int i = 0; i < numEntries; i++) {
	            if (directoryMap.getInt(currentOffset) != kCDESignature) {
	            	  System.out.println("Missed a central dir sig (at " + currentOffset + ")");
	                throw new IOException();
	            }

	            /* useful stuff from the directory entry */
	            int fileNameLen = directoryMap.getShort(currentOffset + kCDENameLen) & 0xffff;
	            int extraLen = directoryMap.getShort(currentOffset + kCDEExtraLen) & 0xffff;
	            int commentLen = directoryMap.getShort(currentOffset + kCDECommentLen) & 0xffff;

	            /* get the CDE filename */

	            directoryMap.position(currentOffset + kCDELen);
	            directoryMap.get(tempBuf, 0, fileNameLen);
	            directoryMap.position(0);

	            /* UTF-8 on Android */
	            String str = new String(tempBuf, 0, fileNameLen);

	            ZipEntryRO ze = new ZipEntryRO(zipFileName, file, str);
	            ze.mMethod = directoryMap.getShort(currentOffset + kCDEMethod) & 0xffff;
	            ze.mWhenModified = directoryMap.getInt(currentOffset + kCDEModWhen) & 0xffffffffL;
	            ze.mCRC32 = directoryMap.getLong(currentOffset + kCDECRC) & 0xffffffffL;
	            ze.mCompressedLength = directoryMap.getLong(currentOffset + kCDECompLen) & 0xffffffffL;
	            ze.mUncompressedLength = directoryMap.getLong(currentOffset + kCDEUncompLen) & 0xffffffffL;
	            ze.mLocalHdrOffset = directoryMap.getInt(currentOffset + kCDELocalOffset) & 0xffffffffL;
	            if (LOGV) {

//	            	  System.out.println("Filename: " + str);
	            }

	            // set the offsets
	            buf.clear();
	            ze.setOffsetFromFile(f, buf);

	            // put file into hash
//	                    mHashMap.put(str, ze);
	            if(!str.contains("version"))
	                fileNameList.add(getNameNoSuffix(str));

	            // go to next directory entry
	            currentOffset += kCDELen + fileNameLen + extraLen + commentLen;
	        }
	        if (LOGV) {
	        	  System.out.println("+++ zip good scan " + numEntries + " entries");
	        }

	    }

	    private String getNameNoSuffix(String str) {
			// TODO Auto-generated method stub
	    	String result = null;
	    	result = str.substring(0,str.indexOf("."));
	    	System.out.println("filenameNoSuffix" + result);
			return result;
		}

		static public final class ZipEntryRO {
	        public ZipEntryRO(final String zipFileName, final File file, final String fileName) {
	            mFileName = fileName;
	            mZipFileName = zipFileName;
	            mFile = file;
	        }

	        public final File mFile;
	        public final String mFileName;
	        public final String mZipFileName;
	        public long mLocalHdrOffset; // offset of local file header

	        /* useful stuff from the directory entry */
	        public int mMethod;
	        public long mWhenModified;
	        public long mCRC32;
	        public long mCompressedLength;
	        public long mUncompressedLength;

	        public long mOffset = -1;

	        public void setOffsetFromFile(RandomAccessFile f, ByteBuffer buf) throws IOException {
	            long localHdrOffset = mLocalHdrOffset;
	            try {
	                f.seek(localHdrOffset);
	                f.readFully(buf.array());
	                if (buf.getInt(0) != kLFHSignature) {
	                	  System.out.println("didn't find signature at start of lfh");
	                    throw new IOException();
	                }
	                int nameLen = buf.getShort(kLFHNameLen) & 0xFFFF;
	                int extraLen = buf.getShort(kLFHExtraLen) & 0xFFFF;
	                mOffset = localHdrOffset + kLFHLen + nameLen + extraLen;
	            } catch (FileNotFoundException e) {
	                e.printStackTrace();
	            } catch (IOException ioe) {
	                ioe.printStackTrace();
	            }
	        }


	    }



	    static private int read4LE(RandomAccessFile f) throws EOFException, IOException {
	        return swapEndian(f.readInt());
	    }


	    public void createXMLFile(String fileName,ArrayList<String> fileNameList){
	        org.jdom.Element root = new org.jdom.Element("watchface");
	        Document document = new Document(root);
	        org.jdom.Element elementName = new org.jdom.Element("name");
	        elementName.setText(fileName);
	        root.addContent(elementName);
	        org.jdom.Element elementFile = new org.jdom.Element("files");
	        for(int i = 0;i < fileNameList.size();i++){
	            org.jdom.Element elementItem = new org.jdom.Element("item");
	            elementItem.setText(fileNameList.get(i));
	            elementFile.addContent(elementItem);
	        }
	        root.addContent(elementFile);

	        XMLOutputter xmlOutputter = null ;
	        Format format = Format.getCompactFormat();
	        format.setIndent("   ");
	        xmlOutputter = new XMLOutputter(format);

	        try{
	            xmlOutputter.output(document,new FileOutputStream("E:\\xml\\"+fileName+".xml"));
	        }catch (Exception e){
	            e.printStackTrace();
	        }

	    }

}
