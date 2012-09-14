import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Rewriter {

	private static final String REWRITE_REGEX = "<!--\\s?@@@\\s?REWRITE\\s([\\s\\S]*?)\\s?@@@\\s?-->((?:\r\n|\n|\r)*[\\s\\S]*?)<!--\\s?@@@\\s?END\\sREWRITE\\s?@@@\\s?-->";
	private static final String FILE_PATH_REGEX = "(?:href|src)=\"?([^\"\\s]*)";
	private static String sourceFile;
	private static String sourceText;
	private static HashSet<RewriteRegion> regions = new HashSet<RewriteRegion>();
	
	private static final String CSS_REPLACE = "<link rel=\"stylesheet\" type=\"text/css\" media=\"all\"  href=\"%s?v=%s\" />";
	private static final String JS_REPLACE = "<script type=\"text/javascript\" src=\"%s?v=%s\"></script>";
    	
	/**
	 * @param args
	 * @throws IOException 
	 * 
	 */
	public static void main(String[] args) throws IOException{
		
		if(args.length != 1) {
            System.err.println("Rewriter accepts exactly one argument: the filename of a document containing HTML.");
            System.exit(1);
        }
		sourceFile = args[0];
        sourceText = readSource(sourceFile);
        Pattern re = Pattern.compile(REWRITE_REGEX, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
		Matcher m = re.matcher(sourceText);
		
		//find regions to rewrite
		while(m.find())
		{
			String src = m.group(0);
			String outputFile = m.group(1);
			String inputFiles = m.group(2);
			RewriteRegion region = new RewriteRegion(src, outputFile, inputFiles);
			regions.add(region);
		}
		
		rewriteRegions();
		
	}
	
	private static void rewriteRegions() throws IOException {
		
		if(regions.isEmpty())
		{
			return;
		}
		
		long ts = System.currentTimeMillis();
		String buildNo = Long.toString(ts);
		String out = "";
		for (RewriteRegion region : regions) {
			String outputFile = region.getOutputFile();
			String replacement = outputFile.endsWith(".css") ? CSS_REPLACE : JS_REPLACE;
			
			replacement = String.format(replacement, outputFile, buildNo);
			sourceText = sourceText.replace(region.getSource(), replacement);
			out += outputFile + "=";
			for(String file: region.getInputFiles()){
				out += (file + ",");  
			}
			out = out.substring(0,out.length()-1) +  "|";
		}
		System.out.println(out.substring(0, out.length()-1));
		
		BufferedWriter writer = new BufferedWriter(new FileWriter(sourceFile));
		writer.write(sourceText);
		writer.close();
	}
	
	private static String readSource(String filePath) throws IOException {
		String out = "";
		BufferedReader in = new BufferedReader(new FileReader(filePath));
		try 
		{
			String str;
			while((str = in.readLine()) != null){
				out += str + "\n";
			}
		}finally{
			in.close();
		}
		return out;
		/* ---- problems on windows - fc not closing and forcing gc doesn't help:
		FileInputStream stream = new FileInputStream(new File(filePath));
		try {
			FileChannel fc = stream.getChannel();
			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0,fc.size());
			return Charset.defaultCharset().decode(bb).toString();
		} finally {
			stream.close();
		}
		*/
	}
	
	private static class RewriteRegion {
		private String src;
		private String outputFile;
		private HashSet<String> inputFiles = new HashSet<String>();
		
		public RewriteRegion(String src, String outputFile, String inputFiles) {
			this.src = src;
			this.outputFile = outputFile;
			String[] files =  inputFiles.trim().split("\\r?\\n|\\r");
			Pattern re = Pattern.compile(FILE_PATH_REGEX);
			for(String file : files){
				Matcher m = re.matcher(file);
				m.find();
				this.inputFiles.add(m.group(1));
			}
		}
		
		public String getSource()
		{
			return this.src;
		}
		
		public String getOutputFile()
		{
			return this.outputFile;
		}
		
		public HashSet<String> getInputFiles()
		{
			return this.inputFiles;
		}
	}
}
