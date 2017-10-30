import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Main class.
 * */
public class CleanUp {
	
	public static boolean DEBUGABLE = true;
	
	public static final int SIZE_OF_1M = 1024 * 1024;
	
	private final ConfReader confrd;
	private final DataWriter datwt;

	public static void main(String[] args) {
		/*
		 * 随机生成数据，用于填满磁盘空间，防止已被删除的数据被恢复。
		 * */
		long start = System.currentTimeMillis();
		System.out.println("-- begin --\n");
		
		CleanUp main = new CleanUp();
		main.fillWithData();
		
		long end = System.currentTimeMillis();
		System.out.println("\n--- End --- " + parseTime(end - start));
	}

	private void fillWithData() {
		if(DEBUGABLE)
			System.out.println("filling in random data.");
		datwt.write(confrd);
	}

	{
		confrd = new ConfReader();
		datwt = new DataWriter();
		//Get the debug switch.
		DEBUGABLE = confrd.getDebugable();
	}

	/**
	 * 解析 System.currentTimeMills 得到的时间为易于阅读模式。
	 * */
	private static String parseTime(long time) {
		if(time > 1000) {
			long ms = time % 1000;
			long seconds = time / 1000;
			if(seconds > 60) {
				long mins = seconds / 60;
				seconds %= 60;
				if(mins > 60) {
					long hours = mins / 60;
					mins %= 60;
					return hours + "h " + mins + "m " +seconds + "s";
				}else {
					return mins + "m " + seconds + "s";
				}
			}else {
				return seconds + "s " + ms + "ms";
			}
		}else {
			return time + "ms";
		}
	}
	
}

/**
 * 写数据到磁盘。
 * */
class DataWriter {
	
	private String outputRoot;
	private String prefix;
	private String suffix;
	
	private boolean appendMode;
	
	/**要写到磁盘的数据字符范围*/
	private int dataCodeRange;
	/**写出去的单个文件的大小*/
	private long sizeOfSingleFile;
	/**写出去的文件数量*/
	private int amountOfFiles;
	/**单次write的数据长度。*/
	private int WRITE_ONE_TIME_LEN = 128 * 1024;//128k
	
	private final RandomGenerator randgr;
	
	{
		randgr = new RandomGenerator();
	}

	public void write(ConfReader cr) {
		applyProp(cr);
		// 组装输出路径。
		int counter = 0;
		if(appendMode)
			counter = getCounter();
		if(CleanUp.DEBUGABLE)
			System.out.println("The counter begin with:" + counter);
		
		drawWriting();
		//write data.
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		while(counter < amountOfFiles) {//The amount of files.
			if(amountOfFiles != Integer.MAX_VALUE)
				counter++;
			try {
				fos = new FileOutputStream(new File(outputRoot + prefix + counter + "." +suffix));
				bos = new BufferedOutputStream(fos, CleanUp.SIZE_OF_1M);
				long tmpLen = 0;
				while(tmpLen < sizeOfSingleFile) {
					if(sizeOfSingleFile < WRITE_ONE_TIME_LEN)
						WRITE_ONE_TIME_LEN = (int)sizeOfSingleFile;
					
					tmpLen += WRITE_ONE_TIME_LEN;
					bos.write(randgr.getRandomCharactor(dataCodeRange, WRITE_ONE_TIME_LEN));
					bos.flush();
				}
			}catch(FileNotFoundException e) {
				e.printStackTrace();
				break;
			} catch (IOException e) {
				break;
			}finally {
				try {
					fos.flush();
					bos.flush();
					fos.close();
					bos.close();
				}catch(Exception e) {
					
				}
			}
		}//End of while.
	}

	private void applyProp(ConfReader cr) {
		// read the configuration of append files mode in 'conf_.ini'.
		String am = cr.getStringProperty("append_files_in_sequence", "false");
		appendMode = Boolean.valueOf(am);
		if(CleanUp.DEBUGABLE)
			System.out.println("appendMode:" + appendMode);
		
		//Get output path.
		outputRoot = cr.getStringProperty("output", "null");
		if(CleanUp.DEBUGABLE)
			System.out.println("The output path in conf_.ini :" + outputRoot);
		File opf = new File(outputRoot);
		if(!opf.exists() || !opf.isDirectory()) {
			//Get current workspace partition root path.
			File current = new File("");
			String split[] = current.getAbsolutePath().split(":");
			if(split.length > 1)
				outputRoot = split[0] + ":\\";
			else
				outputRoot = split[0] + "/";
		}
		if(CleanUp.DEBUGABLE)
			System.out.println("output path:" + outputRoot);
		
		//Get output file name prefix and suffix.
		prefix = cr.getFilePrefix();
		suffix = cr.getFileSuffix();
		
		//Get the output files size information.
		dataCodeRange = cr.getDataCodeRange();
		sizeOfSingleFile = cr.getSizeOfSingleFile();
		amountOfFiles = cr.getAmountOfFiles();
		//当配置文件中amountOfFiles的值为-1时，则持续写，直到磁盘写满。
		amountOfFiles = amountOfFiles == -1?Integer.MAX_VALUE:amountOfFiles;
		if(CleanUp.DEBUGABLE)
			System.out.println("data code range:[0," + dataCodeRange + ")"
					+ ",amount of files: " + amountOfFiles
					+ " ,the size of single file: " + sizeOfSingleFile + " byte(s).");
	}
	
	/**
	 * 读取当前盘中是否已存在本程序写的文件，若存在，
	 * 续写文件而不是从0开始写。
	 * */
	private int getCounter() {
		File root = new File(outputRoot);
		final String rf[] = root.list();
		if(rf == null || rf.length == 0)
			return 0;
		if(CleanUp.DEBUGABLE)
			System.out.println(outputRoot + " partition file amount:" + rf.length);
//		for(String n:rf)
//			System.out.println(n);
		//Get the max serial number.
		final int len = rf.length;
		int max = 0;
		for(int i = 0; i < len; i++) {
			if(rf[i].startsWith(prefix) && rf[i].endsWith(suffix)) {
				//Pick the sequence number.
				String serial = rf[i].substring(prefix.length(), rf[i].length() - (suffix.length() + 1));
				try {
					int serialN = Integer.valueOf(serial);
					if(max < serialN)
						max = serialN;
				}catch(NumberFormatException e) {
					continue;
				}
			}
		}
		if(CleanUp.DEBUGABLE)
			System.out.println("The last serial number in existed file:" + max);
		if(amountOfFiles != Integer.MAX_VALUE)
			amountOfFiles += max;
		return max;
	}
	
	private void drawWriting() {
		System.out.println("******************************");
		System.out.println("***       writing...       ***");
		System.out.println("******************************");
	}
	
}//End of class DataWriter.

/**
 * 读取外置配置文件信息。
 * */
class ConfReader {
	
	private static final String CONF_FN1 = "src/conf_.ini";
	private static final String CONF_FN2 = "conf_.ini";
	private static final String CONF_FN3 = "../src/conf_.ini";
	
	private Properties prop;
	
	public ConfReader() {
		open();
	}
	
	private void open(){
		FileInputStream fis;
		try {
			File conf = new File(CONF_FN1);
			if(!conf.exists()) {
				conf = new File(CONF_FN2);
				if(!conf.exists()) {
					conf = new File(CONF_FN3);
					if(!conf.exists())
						throw new FileNotFoundException("'conf_.ini' not found!!!!");
				}
			}
			fis = new FileInputStream(conf);
			prop = new Properties();
			prop.load(fis);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Properties getProp() {
		if(prop == null)
			open();
		return prop;
	}

	public int getDataCodeRange() {
		return getIntegerProperty("data_code_range");
	}

	public long getSizeOfSingleFile() {
		String unit = getStringProperty("single_file_size_unit").toLowerCase();
		long unitl;
		if("1k".equals(unit)) {
			unitl = 1024;//1024bytes
		}else if("1m".equals(unit)) {
			unitl = 1024 * 1024;
		}else if("1b".equals(unit)) {
			unitl = 1;// 1 byte.
		}else if("1g".equals(unit)) {
			unitl = 1024 * 1024 * 1024;// 1GB.
		}else {
			unitl = 256 * 1024;// Default is 256kb.
			if(CleanUp.DEBUGABLE)
				System.out.println("The unit in 'conf_ini' incorrect,set it to default:256kb" );
		}
		
		if(CleanUp.DEBUGABLE)
			System.out.println("The unit in integer:" + unitl);
		
		int sizei = getIntegerProperty("size_of_single_file");
		sizei *= unitl;
		if(CleanUp.DEBUGABLE)
			System.out.println("size of single fine in integer:" + sizei);
		return sizei;
	}

	public int getAmountOfFiles() {
		return getIntegerProperty("amount_of_files");
	}
	
	public String getFilePrefix() {
		return getStringProperty("fn_prefix");
	}

	public String getFileSuffix() {
		return getStringProperty("fn_suffix");
	}
	
	public boolean getDebugable() {
		String debug = prop.getProperty("debugable", "false");
		return Boolean.valueOf(debug);
	}
	
	public String getStringProperty(String name) {
		String value = prop.getProperty(name);
		if(value == null)
			throw new NullPointerException("Can't find " + name);
		if(CleanUp.DEBUGABLE)
			System.out.println(name + ":" + value);
		return value;
	}
	
	public String getStringProperty(String name, String def) {
		String value = prop.getProperty(name, def);
		if(CleanUp.DEBUGABLE)
			System.out.println(name + ":" + value);
		return value;
	}
	
	public int getIntegerProperty(String name) {
		int value;
		try {
			value = Integer.valueOf(getStringProperty(name));
		}catch(NumberFormatException e) {
			throw new IllegalArgumentException(name + " value illegal!");
		}
		return value;
	}
}// Configuration file reader end.

/**
 * 随机数据生成器。
 * */
class RandomGenerator {
	
	public byte getRandomCharIndex(long range) {
		byte random = (byte) (range * Math.random());
		return random;
	}
	
	/**
	 * @return 返回指定长度的字节数组。
	 * @param range 想得到的数值的最大值。[0,range)。一般填入255已经能够得到很完美的无规律数据。
	 * @param length 想要得到多少个字节的数据。
	 * */
	public byte[] getRandomCharactor(long range, int length) {
		if(length <= 0)
			throw new IllegalArgumentException("length:" + length);
		byte buf[] = new byte[length];
		for(int i = 0; i < length; i++) {
			buf[i] = getRandomCharIndex(range);
		}
		
		return buf;
	}
}
