package org.kered.unsprung;
import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;


public class UnSprungAntTask extends Task {
	
	private org.apache.tools.ant.types.FileSet src = null;
	private File dest = null;
	
	public void addInner( org.apache.tools.ant.types.FileSet src ) {
		this.src = src;
	}
	
	public void setDestination(File dest) {
		this.dest = dest;
	}

	@Override
	public void execute() throws BuildException {
		System.out.println("woot!");
	}


}
