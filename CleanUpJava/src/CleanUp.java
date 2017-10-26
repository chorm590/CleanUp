import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

public class CleanUp {
	
	private static final long MAX_LENGTH = Long.MAX_VALUE;
	private static final int SIZE_OF_1M = 1024 * 1024;
	private static final long NUMBER_RANGE = 256;//0~255
	private static final long SIZE_OF_SINGLE_FILE = 1 * SIZE_OF_1M;//1MB.
	/**你想写多少个{@link CleanUp.SIZE_OF_SINGLE_FILE} 大小的文件到磁盘中？*/
	private static final long AMOUMT_OF_FILES = 1024;
	
	private static final String FILE_NAME_PREFIX = "clean_partition";
	private static final String FILE_SUFFIX = ".override";
	
	
	private final RandomGenerator rg;

	public static void main(String[] args) {
		/*
		 * 随机生成数据，用于填满磁盘空间，防止已被删除的数据被恢复。
		 * */
		System.out.println(MAX_LENGTH);
		CleanUp cleanup = new CleanUp();
		cleanup.init();
	}
	
	{
		rg = new RandomGenerator();
	}
	
	void init() {
		long start = System.currentTimeMillis();
		System.out.println("-- Begin --");
		File file = new File("");
		String workingPath = file.getAbsolutePath();
		String workingStorageRoot = getRoot(workingPath);
//		fillIt(workingStorageRoot + ":\\");// Like ' C: '
		fillIt("d:\\hehe\\");
		long end = System.currentTimeMillis();
		System.out.println("--- End --- " + parseTime(end - start));
	}

	/**
	 * 解析 System.currentTimeMills 得到的时间为易于阅读模式。
	 * */
	private String parseTime(long time) {
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

	private String getRoot(String workingPath) {
		if(null == workingPath)
			throw new NullPointerException("Your working directory incorrect.");
		return workingPath.split(":")[0];
	}

	private void fillIt(String workingStorageRoot) {
		long counter = 0;
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		do {
			//Write a file into the root storage.
			try {
				fos = new FileOutputStream(new File(workingStorageRoot + FILE_NAME_PREFIX + counter + FILE_SUFFIX));
				bos = new BufferedOutputStream(fos, SIZE_OF_1M);
				bos.write(rg.getRandomCharactor(NUMBER_RANGE, SIZE_OF_1M));
				bos.flush();
				fos.flush();
				bos.close();
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}while(AMOUMT_OF_FILES > counter++);
	}
	
}

class RandomGenerator {
	
	public byte getRandomCharIndex(long range) {
		byte random = (byte) (range * Math.random());
		return random;
	}
	
	/**
	 * @param range 想得到的数值的最大值。由0开始。
	 * @param length 想要得到多少个字节的数据。
	 * */
	public byte[] getRandomCharactor(long range, int length) {
		if(length <= 0)
			throw new IllegalArgumentException("fuck you,length:" + length);
		byte buf[] = new byte[length];
		for(int i = 0; i < length; i++) {
			buf[i] = getRandomCharIndex(range);
		}
		
		return buf;
	}
}
