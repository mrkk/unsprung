package org.kered.unsprung;
import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;


public class UnSprungAntTask extends Task {
	
	private org.apache.tools.ant.types.FileSet src = null;
	private File dest = new File("GeneratedSpringContext.java");
	private File prop = null;
	
	public void addFileset( org.apache.tools.ant.types.FileSet src ) {
		this.src = src;
	}
	
	public void setDestination(File dest) {
		if( dest.isDirectory() ) {
			dest = new File( dest.getAbsolutePath()+System.getProperty("file.separator")+"GeneratedSpringContext.java" );
		}
		if( !dest.getAbsoluteFile().toString().toLowerCase().endsWith(".java") ) {
			throw new RuntimeException("destination must be a java file (ends with '.java')");
		}
		this.dest = dest;
	}

	public void setProperties(File prop) {
		this.prop = prop;
	}

	@Override
	public void execute() throws BuildException {

		UnSprung unSprung = new UnSprung();
		if( prop!=null )
			try {
				unSprung.addProperties(prop);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		DirectoryScanner ds = src.getDirectoryScanner(getProject());
		
		for(String fn : ds.getIncludedFiles()) {
			File file = new File(ds.getBasedir() + System.getProperty("file.separator") + fn);
			try {
				unSprung.addContextFile(file);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		unSprung.generate(dest);
	}


}
