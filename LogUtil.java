package com.lqf.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 功能：
 * 1、如果会频繁调用打印日志的代码，该类可以限制在一段时间内只会打印几条日志。
 * 2、不用在每个类创建一个Logger，而可以直接用该类打印日志。
 * 3、一些类转字符串。
 * 4、读配置文件。
 * 5、计时。
 * 注意：
 * 1、pom依赖slf4j而不依赖log4j，在DOS用log4j打印时会出现乱码，也不会写日志进日志文件。
 * 2、log4j-over-slf4j看来不能读log4j.properties，也不知怎么用。
 * 3、slf4j会使用spring-boot-starter-log4j2、log4j2.xml。
 * 
 * @since: 2017年9月21日
 * @version: 2019-10-06 in lqf-web
 * @author: lqifan
 */
@Controller
@RequestMapping("logUtil")
public class LogUtil {
	/*
	 * org.slf4j.LoggerFactory.getLogger
	 * org.apache.commons.logging.LogFactory.getLog
	 * 以后外部要使用info、warn、error函数，而不能直接调用该成员。
	 */
	private static Logger log = LogUtil.getLogger();
	private static LogUtil instance;
	protected String charset = null;
	protected String path = "log4j.properties";
	protected Properties properties;
	/**
	 * 省略多条信息后，是否为这些信息打印一条概要。
	 */
	private boolean omittedMsgAbstract = true;
	public Map<String, Map<String, Long>> settings = new HashMap<>();

	static {
		/*
		 * eclipse中main运行时字符集是main函数的java文件的字符集；用户目录是workspace里的项目的根目录，末尾没有"\"。
		 * eclipse中Tomcat运行时字符集在Windows上是GBK；用户目录是eclipse.exe所在的eclipse根目录。
		 * jar包运行时字符集在Windows上是GBK；用户目录是jar包所在的目录。
		 */
		String charsetName = Charset.defaultCharset().name();
		String userDir = System.getProperty("user.dir");
		/*
		 * log4j是按默认字符集打印的，所以windows上的jar会按GBK打印，两个应用用不同的字符集写到同一个日志文件，
		 * 生成的日志将不能用一种字符集全部解析。
		 * windows中先用UTF-8打印，记事本会以UTF-8打开正常显示，再用GBK打印，记事本将以GBK打开，新日志正常，旧日志变成乱码，
		 * 再用UTF-8打印后记事本也仍是用GBK打开文件，新日志成乱码。要在其他编辑器用UTF-8打开再删除GBK字符，记事本才能再用UTF-
		 * 8打开。
		 */
		println("defaultCharset=" + charsetName + ",user.dir=" + userDir + ",");

//		List<String> pathList = new ArrayList<>();
//		File file = new File(userDir);
//		pathList.add(file.getAbsolutePath());
//		while (null != file.getParentFile()) {
//			file = file.getParentFile();
//			pathList.add(file.getAbsolutePath());
//		}
		// new Exception().printStackTrace();
		// println(pathList);

		// 有的项目打印不出log日志要加这个
//		BasicConfigurator.configure();
	}

	public LogUtil() {
		{
			Map<String, Long> setting = new HashMap<>();
			setting.put("coolStartMs", 0L);
			setting.put("intervalMs", (long) 30000);
			setting.put("maxNumAmmo", 10L);
			setting.put("numAmmo", setting.get("maxNumAmmo"));
			setting.put("duration", 10000L);
			setting.put("omittedCount", 0L);
			settings.put("info", setting);
		}
		{
			Map<String, Long> setting = new HashMap<>();
			setting.put("coolStartMs", 0L);
			setting.put("intervalMs", (long) 60000);
			setting.put("maxNumAmmo", 5L);
			setting.put("numAmmo", setting.get("maxNumAmmo"));
			setting.put("duration", 10000L);
			setting.put("omittedCount", 0L);
			settings.put("warn", setting);
		}
		{
			Map<String, Long> setting = new HashMap<>();
			setting.put("coolStartMs", 0L);
			setting.put("intervalMs", (long) 150000);
			setting.put("maxNumAmmo", 2L);
			setting.put("numAmmo", setting.get("maxNumAmmo"));
			setting.put("duration", 10000L);
			setting.put("omittedCount", 0L);
			settings.put("error", setting);
		}
	}

	/**
	 * 有的打印日志程序想要在一段时间内限制只打印几条，而不受其他程序干扰，那就自己new一个成员对象。
	 * 如果不介意整个项目的多处打印日志程序在一段时间内只打印几条，就用单例。
	 * 使用SpringBean创建时，如果该类实例还没创建就调用该函数会出错，所以其他类的初始化函数不建议调用这个。
	 * 
	 * @return
	 */
	public static LogUtil getInstance() {
		/*
		 * 如果Factory.getInstance调用了LogUtil，这里可能就不能再调用Factory了。
		 */
//		return Factory.getInstance(LogUtil.class);
		if (null == instance) {
			synchronized (LogUtil.class) {
				if (null == instance && null != SpringContextUtil.getApplicationContext()) {
					instance = (LogUtil) SpringContextUtil.getBean("logUtil");
				}
				if (null == instance) {
					instance = new LogUtil();
				}
			}
		}
		return instance;
	}

	/**
	 * 用来获取Logger。
	 * 
	 * <pre>
	 * protected static Logger log = LogUtil.getLogger();
	 * </pre>
	 * 
	 * @return
	 */
	public static Logger getLogger() {
		Exception ex = new Exception();
		// 静.<clinit>，动.<init>
		String className = ex.getStackTrace()[1].getClassName();
		Logger log = null;
		try {
			Class<?> stackClazz = Class.forName(className);
			// PatternLayout.format会为信息加前缀
			log = Logger.getLogger(stackClazz);
			// log.info(stackClazz, ex);
		} catch (Exception e) {
			println(toString(e, 0, 1));
			throw new RuntimeException(e);
		}
		return log;
	}

	// ----字符串操作
	/**
	 * 
	 * @param level 填null生成简单的前缀
	 * @return
	 */
	private static String createPrefix(String level) {
		StackTraceElement[] stackElements = new Exception().getStackTrace();
		int stackDepth = stackElements.length - 1;
		for (int i = 1; i < stackDepth; i++) {
			StackTraceElement stackElement = stackElements[i];
			if (!LogUtil.class.getName().equals(stackElement.getClassName())
					|| "<clinit>".equals(stackElement.getMethodName())) {
				stackDepth = i;
				break;
			}
		}
		// 文件名不会在控制台生成链接，“文件名:行号”就会。
		if (null == level || "" == level) {
			return "(" + stackElements[stackDepth].getFileName() + ":" + stackElements[stackDepth].getLineNumber()
					+ ")- ";
		}
		Date now = new Date();
		SimpleDateFormat df = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
		return df.format(now) + String.format("%-5s", level.toUpperCase()) + "("
				+ stackElements[stackDepth].getFileName() + ":" + stackElements[stackDepth].getLineNumber() + ")- ";
	}

	/**
	 * 
	 * @param obj
	 * @param maxLength 考虑到这个尾部有固定的格式，数组首部有固定格式，这个至少设为40
	 * @return
	 */
	public static String omit(Object obj, int maxLength) {
		if (null == obj) {
			return null;
		}
		String str = obj.toString();
		if (0 < maxLength && maxLength < str.length()) {
			String tail = "...(length=" + str.length() + ")";// 可能有20的长度
			return str.substring(0, maxLength - tail.length()) + tail;
		}
		return str;
	}

	/**
	 * 为了小于或等于，要除以的10的整次幂是多少呢？
	 * 
	 * @param dividend
	 * @param le
	 * @return
	 */
	private static int divWhatLe(int dividend, int le) {
		return LqMath.powToGe(10, LqMath.roundUp(dividend, le));
	}

	/**
	 * 主要用于测试时打印，相比正式的转字符串，这个带省略、换行。
	 * 
	 * @param obj
	 * @return
	 */
	public static String toString(Object obj) {
		if (null == obj) {
			return "null";
		} else if (obj instanceof Byte) {
			String str = Integer.toHexString((byte) obj);
			if (1 == str.length()) {
				str = "0" + str;
			} else if (2 < str.length()) {
				str = str.substring(str.length() - 2);
			}
			return ("0x" + str);
		} else if (obj.getClass().isArray()) {
			return toString(obj, 20, 500);
		} else if (obj instanceof Collection) {// 可以是List或Set，数组和Map不行
			Collection<?> list = (Collection<?>) obj;
			return toString(list, 20, 500);
		} else if (obj instanceof Iterable) {// 可以是List、Set，数组和Map不行
			Iterable<?> iterable = (Iterable<?>) obj;
			Iterator<?> iterator = iterable.iterator();
			int size = 0;
			while (iterator.hasNext()) {
				iterator.next();
				size++;
			}
			return "iterable.size=" + size;
		} else if (obj instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) obj;
			StringBuilder sb = new StringBuilder();
			sb.append("(" + map.getClass().getSimpleName() + ".size=" + map.size() + "){");
			int i = 0;
			for (Object key : map.keySet()) {
				if (0 < i) {
					sb.append(", ");
				}
				Object value = map.get(key);
				if (null == value) {

				} else if (value instanceof String) {
					value = "\"" + omit(((String) value).replaceAll("\"", "\\\\\""), 100) + "\"";
				} else if (value.getClass().isArray()) {
					value = toString(value);
				} else {
					value = omit(value, 100);
				}
				sb.append(key + "=" + value);
				i++;
			}
			sb.append("}");
			return sb.toString();
		} else if (obj instanceof Throwable) {
			return toString((Throwable) obj, 0, -1);
		} // 对于实体，待完善
		return obj.toString();
	}

	/**
	 * <pre>
	 * println(toString(e, 0, -1));
	 * </pre>
	 * 
	 * @param obj
	 * @param stackDepthGe 这里的堆栈对于有堆栈的数据，指的就是它的堆栈，对于其他数据，出错时指这个错误的堆栈。
	 *            对于数组和List，这个是最大显示数量。
	 * @param stackDepthLt
	 *            对于数组和List，这个是最大列数；对于字节数组，这个设置为(每行个数+1)*5+2可以比较整齐地查看100000条数据
	 * @return
	 */
	public static String toString(Object obj, int stackDepthGe, int stackDepthLt) {
		StringBuilder message = new StringBuilder();
		StackTraceElement[] stackElements = null;
		if (null == obj) {
			return "null";
		} else if (obj instanceof StackTraceElement[]) {
			stackElements = (StackTraceElement[]) obj;
		} else if (obj instanceof Thread) {
			Thread thread = (Thread) obj;
			stackElements = thread.getStackTrace();
			message = message.append(obj);
		} else if (obj.getClass().isArray()) {
			int maxNum = stackDepthGe;
			int maxCols = stackDepthLt;
			Object[] arr = null;
			try {
				if (obj instanceof byte[]) {
					arr = ArrayUtils.toObject((byte[]) obj);
					message.append("(byte[].length=");
				} else if (obj instanceof char[]) {// char默认值为0，显示为一个矩形。
					arr = ArrayUtils.toObject((char[]) obj);
					message.append("(char[].length=");
				} else if (obj instanceof short[]) {
					arr = ArrayUtils.toObject((short[]) obj);
					message.append("(short.length=");
				} else if (obj instanceof int[]) {
					arr = ArrayUtils.toObject((int[]) obj);
					message.append("(int[].length=");
				} else if (obj instanceof long[]) {
					arr = ArrayUtils.toObject((long[]) obj);
					message.append("(long[].length=");
				} else if (obj instanceof double[]) {
					arr = ArrayUtils.toObject((double[]) obj);
					message.append("(double[].length=");
				} else {
					arr = (Object[]) obj;
					message.append("(Object[].length=");
				}
				message.append(arr.length + ")[");
				List<StringBuilder> lines = new ArrayList<>();
				StringBuilder firstSep = new StringBuilder();
				for (int i = 0; i < arr.length && i < maxNum + 1; i++) {
					Object e = arr[i];
					String value;
					if (i < maxNum) {
						value = toString(e);
					} else {
						value = "...";
					}
					int linesSize = lines.size();
					StringBuilder line = null;
					if (0 == linesSize
							|| (0 < maxCols && maxCols < lines.get(linesSize - 1).length() + value.length() + 2)) {
						line = new StringBuilder();
						line.append("[" + (i + 1) + "]");
						lines.add(line);
						firstSep.append("]");
					} else {
						line = lines.get(linesSize - 1);
					}
					LqStringUtil.append(line, firstSep, ",", value);
				}
				for (int i = 0; i < lines.size(); i++) {
					if (1 < lines.size()) {
						message.append("\r\n");
					}
					message.append(omit(lines.get(i), maxCols - 1));
					if (i < lines.size() - 1) {
						message.append(",");
					} else {
					}
				}
				message.append("]");
			} catch (Exception e) {
				obj = e;
				stackDepthGe = 0;
				stackDepthLt = -1;
			}
		} else if (obj instanceof Collection) {
			int maxNum = stackDepthGe;
			int maxCols = stackDepthLt;
			Collection<?> list = (Collection<?>) obj;

			int listSize = list.size();
			int divisor = divWhatLe(listSize, maxNum);
			List<StringBuilder> lines = new ArrayList<>();
			int li = 0;
			for (Object e : list) {
				if (0 == li % divisor) {
					String value = "[" + (li + 1) + "]" + toString(e);
					int linesSize = lines.size();
					StringBuilder line = null;
					if (0 == linesSize
							|| (0 < maxCols && maxCols < lines.get(linesSize - 1).length() + value.length())) {
						line = new StringBuilder();
						lines.add(line);
					} else {
						line = lines.get(linesSize - 1);
					}
					line.append(value);
				}
				li++;
			}
			message.append("list.size=" + listSize + "[");
			for (int i = 0; i < lines.size(); i++) {
				if (1 < lines.size()) {
					message.append("\r\n");
				}
				message.append(omit(lines.get(i), maxCols));
			}
			message.append("]");
		} else if (obj instanceof File) {
			try {
				message.append(toString(FileUtils.readFileToByteArray((File) obj), stackDepthGe, stackDepthLt));
			} catch (IOException e) {
				obj = e;
				stackDepthGe = 0;
				stackDepthLt = -1;
			}
		} else {
			obj = new Throwable();
			stackDepthGe = 0;
			stackDepthLt = -1;
		}
		if (obj instanceof Throwable) {
			Throwable th = (Throwable) obj;
			stackElements = th.getStackTrace();
			message = message.append(obj);// th=类名+":
											// "+defaultString(th.getMessage(),"")
		}
		if (stackElements != null) {
			if (stackDepthLt < 0) {
				stackDepthLt = stackDepthGe + 3;
			}
			for (int i = stackDepthGe; i < stackDepthLt && i < stackElements.length; i++) {
				message.append("\r\n\tat " + stackElements[i].getClassName() + "." + stackElements[i].getMethodName()
						+ "(" + stackElements[i].getLineNumber() + ")");
			}
		}
		return message.toString();
	}

	public static RuntimeException throwIt(String message, Throwable t) {
		RuntimeException r = null;
		if (t instanceof RuntimeException && null == message) {
			println(toString(t, 0, 2));
			r = (RuntimeException) t;
		} else {
			r = new RuntimeException(message, t);
		}
		throw r;
	}

	/**
	 * 方便测试时打印，10000次耗时378ms
	 * 
	 * @param obj
	 */
	public static void println(Object obj) {
		String prefix = createPrefix("");
		if (null == obj) {
			System.out.println(prefix + "null");
		} else {
			System.out.println(prefix + toString(obj));
		}
	}

	// ----关于属性
	/**
	 * 将配置文件的数据写入该对象的成员变量
	 */
	protected void init() {
		this.properties = readProperties();
	}

	/**
	 * 修改属性。
	 * 这里不同步的话，可能第一个线程获得空properties后卡住，第二个线程越过if的内容继续执行使用空properties将出错。
	 */
	private void init2() {
//		if (null == properties) {
		String[] appenderNames = getProperty("log4j.rootLogger").split(",");
		Properties prop = new Properties();
		for (int i = 1; i < appenderNames.length; i++) {
			String appenderName = appenderNames[i].trim();
			String key = "log4j.appender." + appenderName + ".layout.ConversionPattern";
			prop.setProperty(key, "[%d{yyyy-MM-dd HH:mm:ss}]%-5p%m\r\n");
//			setProperty(key, "[%d{yyyy-MM-dd HH:mm:ss}]%-5p%m\r\n");
		}
		setProperties(prop);
//			setProperty("log4j.appender.logfile.MaxFileSize", "2KB");
//			setProperty("log4j.appender.logfile.MaxBackupIndex", "2");
//		}
	}

	private void restoreProperties() {
		properties = readProperties();
		PropertyConfigurator.configure(properties);
	}

	/**
	 * 这个会先在资源目录下找，再在系统跟目录下找。
	 * 
	 * @param filePath
	 * @return
	 */
	public static InputStream openInputStream(String filePath) {
		// 从CsvAccessor拷过来改的
		try {
			// 输入流不关闭或函数结束来自动释放，则文件不能修改后保存
			// getResourceAsStream读出来是BufferedInputStream
			// 用Object获得Resource在main函数可以，在服务器运行就会找不到文件。
			InputStream inputStream = LogUtil.class.getResourceAsStream("/" + filePath);
			// 不要去user.dir找了
			// inputStream = new FileInputStream(System.getProperty("user.dir")
			// + File.separator + filePath);
			if (null == inputStream) {
				inputStream = new FileInputStream(filePath);
			}
			return inputStream;
		} catch (Exception e) {
			LogUtil.throwIt(LogUtil.toString(e, 0, 1), e);
			return null;
		}
	}

	public static String readFileToString(String filePath, String charsetName) {
		charsetName = GlobalParams.defaultCharsetName(charsetName);
		try {
			String str = IOUtils.toString(openInputStream(filePath), charsetName);
			return str;
		} catch (Exception e) {
			println(LogUtil.toString(e, 0, 1));
			throw new RuntimeException(e);
		}
	}

	/**
	 * 最初做出来给爬虫读文件中的网址。
	 * 
	 * @param filePath
	 * @param charset
	 * @return
	 */
	public static List<String> readValidLines(String filePath, String charset) {
		if (null == charset) {
			charset = Charset.defaultCharset().name();
		}
		List<String> lineList = new ArrayList<>();
		String line = null;
		InputStream istream = openInputStream(filePath);
		try {
			// 请根据文件编码格式在这里传入正确的参数
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(istream, charset));
			while ((line = bufferedReader.readLine()) != null) {
				line = line.trim();
				if ("".equals(line) || line.startsWith("#") || line.startsWith("//") || line.startsWith("--")
						|| line.startsWith(";")) {
					continue;
				}
				lineList.add(line);
			}
		} catch (Exception e) {
			println(toString(e, 0, 1));
			throw new RuntimeException(e);
		}
		return lineList;
	}

	/**
	 * 日志有时要重读配置文件，所以基类的这个做成不缓存的。
	 * 
	 * @return
	 */
	protected Properties readProperties() {
		return readProperties(path, charset);
	}

	public static Properties readProperties(String path, String charset) {
		if (null == charset) {
			charset = "UTF-8";// 以UTF-8的方式也能读ISO-8859-1的"\\uXXXX"
		}
		InputStream inputStream = null;
		Properties properties = null;
		try {
			inputStream = openInputStream(path);
			properties = new Properties();
			properties.load(new InputStreamReader(inputStream, charset));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (null != inputStream) {
				try {
					inputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				inputStream = null;
			}
		}
		return properties;
	}

	/**
	 * 修改一个日志的配置参数，多次调用该函数，也只能修改最后一个参数。
	 * 
	 * <pre>
	 * LogUtil.modifyAProperty("log4j.appender.file.File", "D:/logs/webapp.log");
	 * </pre>
	 * 
	 * @deprecated 用setProperty代替
	 * @param key
	 * @param value
	 */
	public void modifyAProperty(String key, String value) {
		Properties properties = readProperties();
		properties.setProperty(key, value);
		PropertyConfigurator.configure(properties);
	}

	/**
	 * 在配置文件的基础上修改日志的配置参数。
	 * 
	 * <pre>
	 * LogUtil.setProperty("log4j.appender.file.File", "D:/logs/webapp.log");
	 * </pre>
	 * 
	 * @param key
	 * @param value
	 */
	public synchronized void setProperty(String key, String value) {
		if (null == properties) {
			properties = readProperties();
		}
		// println("修改" + key + "=" + value);
		properties.setProperty(key, value);
		// 100次可能要1300ms
		PropertyConfigurator.configure(properties);
	}

	public synchronized void setProperties(Properties properties) {
		if (null == this.properties) {
			this.properties = readProperties();
		}
		for (Map.Entry<Object, Object> entry : properties.entrySet()) {
			this.properties.setProperty((String) entry.getKey(), (String) entry.getValue());
		}
		// 100次可能要1300ms
		PropertyConfigurator.configure(this.properties);
	}

	public synchronized String getProperty(String key, String defaultValue) {
		if (null == properties) {
			// init使用文件的配置，init+init2使用该类的配置
			init();
			// init2();
		}
		return properties.getProperty(key, defaultValue);
	}

	public String getProperty(String key) {
		return getProperty(key, null);
	}

	// ----重新实现Logger的函数，主要是改变设置的前缀
	public static void debug(Object message) {
		debug(message, null);
	}

	public static void debug(Object message, Throwable t) {
		getInstance().init2();
		log.debug(createPrefix(null) + message, t);
		getInstance().restoreProperties();
	}

	public static void error(Object message) {
		error(message, null);
	}

	public static void error(Object message, Throwable t) {
		getInstance().init2();
		log.error(createPrefix(null) + message, t);
		getInstance().restoreProperties();
	}

	public static void fatal(Object message) {
		fatal(message, null);
	}

	public static void fatal(Object message, Throwable t) {
		getInstance().init2();
		log.fatal(createPrefix(null) + message, t);
		getInstance().restoreProperties();
	}

	public static void info(Object message) {
		info(message, null);
	}

	/**
	 * 100次耗时1807~1908ms，主要是init2的setProperty耗时，LogUtil.getLogger().info 40~63ms
	 * 
	 * @param message
	 * @param t
	 */
	// 不加同步会同时打印文件和程序配置的前缀
	public static synchronized void info(Object message, Throwable t) {
		getInstance().init2();
		log.info(createPrefix(null) + message, t);
		getInstance().restoreProperties();
	}

	public static void warn(Object message) {
		warn(message, null);
	}

	public static void warn(Object message, Throwable t) {
		getInstance().init2();
		log.warn(createPrefix(null) + message, t);
		getInstance().restoreProperties();
	}
	/*
	 * ----间隔打印，不连续、断断续续、有CD地打印，discretely、with cooling、with intervals
	 * 2020-12-02 不标准英文函数名改正，intervalXxx->xxxWithCool
	 */

	/**
	 * 如果多处共用LogUtil实例，在用的时候可以先使用这个函数设值为0，以保证接下来第一次调用间隔打印一定能打印。
	 * 
	 * @param coolStartMs
	 * @return
	 */
	@RequestMapping("setCoolStartMs")
	@ResponseBody
	public long setCoolStartMs(long coolStartMs) {
		for (String key : settings.keySet()) {
			settings.get(key).put("coolStartMs", coolStartMs);
		}
		return coolStartMs;
	}

	// 函数内不能改变参数的值，封装类也不可以，所以传int不能达到效果。
	/**
	 * 连续打印numAmmo条日志，然后每intervalMs可以再打印一条。
	 * 
	 * @param level
	 * @return
	 */
	protected boolean hasAmmo(String level) {
		Map<String, Long> setting = settings.get(level);
		long coolStartMs = setting.get("coolStartMs");
		long intervalMs = setting.get("intervalMs");
		long numAmmo = setting.get("numAmmo");
		long maxNumAmmo = setting.get("maxNumAmmo");
		long omittedCount = setting.get("omittedCount");
		long nowMs = System.currentTimeMillis();
		long ms = nowMs - coolStartMs;
		if (intervalMs * (maxNumAmmo - numAmmo) <= ms) {
			coolStartMs = nowMs;
			numAmmo = maxNumAmmo;
		} else {
			long addNum = ms / intervalMs;
			coolStartMs += intervalMs * addNum;
			numAmmo += addNum;
		}
		boolean result;
		if (0 < numAmmo) {
			if (0 != omittedCount) {
				if (omittedMsgAbstract) {
					String message = "之前省略了" + omittedCount + "条信息，可打印日志数=" + numAmmo + "/" + maxNumAmmo + "。";
					if ("info".equals(level)) {
						LogUtil.info(message);
					} else if ("warn".equals(level)) {
						LogUtil.warn(message);
					} else if ("error".equals(level)) {
						LogUtil.error(message);
					}
				}
				omittedCount = 0L;
			}
			numAmmo--;
			result = true;
		} else {
			omittedCount++;
			result = false;
		}
		setting.put("coolStartMs", coolStartMs);
		setting.put("numAmmo", numAmmo);
		setting.put("omittedCount", omittedCount);
		return result;
	}

	/**
	 * duration内连续打印maxNumAmmo条日志，然后等到弹药充满后才能再打印日志。
	 * 
	 * @param level
	 * @return
	 */
	protected boolean inDuration(String level) {
		Map<String, Long> setting = settings.get(level);
		long coolStartMs = setting.get("coolStartMs");
		long intervalMs = setting.get("intervalMs");
		long numAmmo = setting.get("numAmmo");
		long maxNumAmmo = setting.get("maxNumAmmo");
		long duration = setting.get("duration");
		long omittedCount = setting.get("omittedCount");
		long nowMs = System.currentTimeMillis();
		long ms = nowMs - coolStartMs;
		if (intervalMs * (maxNumAmmo - numAmmo) <= ms) {
			coolStartMs = nowMs;
			numAmmo = maxNumAmmo;
			ms = nowMs - coolStartMs;
		}
		boolean result;
		if (ms < duration && 0 < numAmmo) {
			if (0 != omittedCount) {
				if (omittedMsgAbstract) {
					String message = "之前省略了" + omittedCount + "条信息，可打印日志数=" + numAmmo + "/" + maxNumAmmo + "。";
					if ("info".equals(level)) {
						LogUtil.info(message);
					} else if ("warn".equals(level)) {
						LogUtil.warn(message);
					} else if ("error".equals(level)) {
						LogUtil.error(message);
					}
				}
				omittedCount = 0L;
			}
			numAmmo--;
			result = true;
		} else {
			omittedCount++;
			result = false;
		}
		setting.put("coolStartMs", coolStartMs);
		setting.put("numAmmo", numAmmo);
		setting.put("omittedCount", omittedCount);
		return result;
	}

	// 普通打印和打印堆栈用了不一样的方法名，当时是想如果还未冷却，打印堆栈虽然不打印堆栈，还能显示一行信息
	/**
	 * 根据打印间隔，剩余弹药数判断，可打印，返回true，否则返回false。
	 * 
	 * @return
	 */
	public boolean infoHasCooled() {
		return inDuration("info");
	}

	/**
	 * 根据打印间隔，剩余弹药数判断，可打印，返回t，否则返回null。
	 * 
	 * @param t
	 * @return
	 */
	public Throwable infoStackHasCooled(Throwable t) {
		if (infoHasCooled()) {
			return t;
		}
		return null;
	}

	/**
	 * 如果弹药不足，将不会打印信息。 这个函数打印的文件、行是该类的文件、行，如果想打印调用类的文件、行，考虑将该函数内容复制粘贴到需要调用的位置。
	 * 
	 * @param log
	 * @param message
	 */
	public void infoWithCool(Object message) {
		if (infoHasCooled()) {
			LogUtil.info(message);
		}
	}

	/**
	 * 如果弹药不足，将不会打印信息。 这个函数会打印该类的文件名、行号，如果想打印的是调用类的文件名、行号，考虑将该函数内容复制粘贴到需要调用的位置。
	 * 
	 * @param log
	 * @param message
	 */
	public void infoStackWithCool(Object message, Throwable t) {
		if (infoHasCooled()) {
			LogUtil.info(message, t);
		}
	}

	/**
	 * 根据打印间隔，剩余弹药数判断，可打印，返回true，否则返回false。
	 * 
	 * @param t
	 * @return
	 */
	public boolean warnHasCooled() {
		return inDuration("warn");
	}

	/**
	 * 根据打印间隔，剩余弹药数判断，可打印，返回t，否则返回null。
	 * 
	 * @param t
	 * @return
	 */
	public Throwable warnStackHasCooled(Throwable t) {
		if (warnHasCooled()) {
			return t;
		}
		return null;
	}

	/**
	 * 如果弹药不足，将不会打印信息。 这个函数打印的文件、行是该类的文件、行，如果想打印调用类的文件、行，考虑将该函数内容复制粘贴到需要调用的位置。
	 * 
	 * @param log
	 * @param message
	 */
	public void warnWithCool(Object message) {
		if (warnHasCooled()) {
			LogUtil.warn(message);
		}
	}

	/**
	 * 如果弹药不足，将不会打印信息。 这个函数会打印该类的文件名、行号，如果想打印的是调用类的文件名、行号，考虑将该函数内容复制粘贴到需要调用的位置。
	 * 
	 * @param log
	 * @param message
	 */
	public void warnStackWithCool(Object message, Throwable t) {
		if (warnHasCooled()) {
			LogUtil.warn(message, t);
		}
	}

	/**
	 * 根据打印间隔，剩余弹药数判断，可打印，返回true，否则返回false。
	 * 
	 * @param t
	 * @return
	 */
	public boolean errorHasCooled() {
		return inDuration("error");
	}

	/**
	 * 根据打印间隔，剩余弹药数判断，可打印，返回t，否则返回null。
	 * 
	 * @return
	 */
	public Throwable errorStackHasCooled(Throwable t) {
		if (errorHasCooled()) {
			return t;
		}
		return null;
	}

	/**
	 * 如果弹药不足，将不会打印信息。 这个函数打印的文件、行是该类的文件、行，如果想打印调用类的文件、行，考虑将该函数内容复制粘贴到需要调用的位置。
	 * 
	 * @param log
	 * @param message
	 */
	public void errorWithCool(Object message) {
		if (errorHasCooled()) {
			LogUtil.error(message);
		}
	}

	/**
	 * 如果弹药不足，将不会打印信息。 这个函数会打印该类的文件名、行号，如果想打印的是调用类的文件名、行号，考虑将该函数内容复制粘贴到需要调用的位置。
	 * 
	 * @param log
	 * @param message
	 */
	public void errorStackWithCool(Object message, Throwable t) {
		if (errorHasCooled()) {
			LogUtil.error(message, t);
		}
	}

	// ----计时
	static ThreadLocal<Long> startMs = new ThreadLocal<>();
	/*
	 * 如果4个计时，每个用3个元素，就要12个。
	 * ms，次数。
	 */
	static ThreadLocal<List<Long>> millis = new ThreadLocal<>();

	// TIC；timer可能是用来做周期动作的
	public static long startStopwatch() {
		long ms = System.currentTimeMillis();
		startMs.set(ms);
		return ms;
	}

	public static long readStopwatch(Object message) {
		long elapsed = (System.currentTimeMillis() - startMs.get());
		StringBuilder sb = new StringBuilder();
		sb.append("elapsed=" + elapsed + "ms");
		if (null != message) {
			sb.append(". " + message);
		}
		LogUtil.info(sb);
		return elapsed;
	}

	public static long resumeStopwatches() {
		if (null == millis.get()) {
			millis.set(new ArrayList<>());
			millis.get().add(0L);
			millis.get().add(0L);
		}
		long ms = System.currentTimeMillis();
		millis.get().set(0, ms);
		millis.get().set(1, millis.get().get(1) + 1);
		return ms;
	}

	public static long pauseStopwatches(int index) {
		// 把get存成变量速度提升不明显，调试时方便查看
		List<Long> list = millis.get();
		for (int i = list.size(); i <= index * 2 + 1; i++) {
			list.add(0L);
		}
		list.set(index * 2, list.get(index * 2) + System.currentTimeMillis() - list.get(0));
		millis.get().set(index * 2 + 1, millis.get().get(index * 2 + 1) + 1);
		return list.get(index);
	}

	public static List<Long> readStopwatches(Object message) {
		StringBuilder sb = new StringBuilder();
		sb.append("elapsed=");
		for (int i = 2; i < millis.get().size(); i += 2) {
			sb.append(millis.get().get(i));
			if (millis.get().size() - 2 > i) {
				sb.append(",");
			} else {
				sb.append("ms,times=");
			}
		}
		for (int i = 3; i < millis.get().size(); i += 2) {
			sb.append(millis.get().get(i));
			if (millis.get().size() - 2 > i) {
				sb.append(",");
			} else {
				sb.append("");
			}
		}
		if (null != message) {
			sb.append(". " + message);
		}
		LogUtil.info(sb);
		millis.set(null);
		return millis.get();
	}

	public static void beep(int times) {
		ThreadUtil threadUtil = new ThreadUtil();
		threadUtil.submit(LogUtil.class, "syncBeep", new Object[] { times });
		threadUtil.getExecutorService().shutdown();
	}

	public static void syncBeep(int times) {
		// 延时1000ms，5次5076~5082ms，10次10088~10100ms
//		long ms = startStopwatch();
		for (int i = 0; i < times; i++) {
			java.awt.Toolkit.getDefaultToolkit().beep();
			ThreadUtil.sleep(1000);
//			if (ms + 1000 * times <= System.currentTimeMillis()) {
//				break;
//			}
		}
//		readStopwatch(null);

	}

	// --下面是测试函数
	public static void test() {
		try {
			for (int i = 0; i < 1; i++) {
				File file = new File("D:\\logs\\lqf-web.log");
				String str = i + ",一二三四五六七八九十," + file.getFreeSpace() + "," + file.getTotalSpace() + ","
						+ file.getUsableSpace() + "," + file.length() + ",";
				Exception ex = new Exception();
				log.info(str + str.length());
				info(str + str.length());
				log.error(str + str.length(), ex);
				error(str + str.length(), ex);
				Thread.sleep(10);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		// 测试printlln
		if (false) {
			List<String> list = new ArrayList<>();
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("1234567890", "123456789012345678901234567890");
			String[] arr = new String[21];
			for (int i = 0; i < 21; i++) {
				list.add(i + "");
				arr[i] = i + "";
				map.put(i + "", i);
			}
			println(toString(list, 3, 50));
			return;
		} else if (false) {
			byte[] bytes = new byte[] { 1, 2, 127 };
			File file = new File("E:\\Program Files (x86)\\Adobe\\PsDocument\\test\\direct.jpg");
			bytes = FileUtils.readFileToByteArray(file);
			println(toString(new File("E:\\Program Files (x86)\\Adobe\\PsDocument\\test\\red.jpg"), 20000, 107));
			// println(LqArrayUtil.toString(bytes));
			// E:\Program Files
			// (x86)\Adobe\PsDocument\testprintln(toString(TestData.arr, 2,
			// 100));
			// println(toString(TestData.strArr, 2, 100));
			// println(toString(TestData.list, 3, 100));
		} else {
			LogUtil.info("---");
			LogUtil.getInstance().infoWithCool("--");
		}

	}

}
