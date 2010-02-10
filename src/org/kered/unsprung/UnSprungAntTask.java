package org.kered.unsprung;
import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;


public class UnSprungAntTask extends Task {
	
	private org.apache.tools.ant.types.FileSet src = null;
	private File dest = new File("GeneratedSpringContext.java");
	private File props = null;
	
	public void addFileset( org.apache.tools.ant.types.FileSet src ) {
		this.src = src;
	}
	
	public void setDestination(File dest) {
		this.dest = dest;
	}

	public void setProperties(File prop) {
		this.props = props;
	}

	@Override
	public void execute() throws BuildException {

		UnSprung unSprung = new UnSprung();
		if( props!=null )
			try {
				unSprung.addProperties(props);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		DirectoryScanner ds = src.getDirectoryScanner(getProject());
		for(String fn : ds.getIncludedFiles()) {
			File file = new File(fn);
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
