package com.mucommander.file;

import java.io.*;
import jcifs.smb.*;

/**
 * SMBFile represents an SMB file.
 */
public class SMBFile extends AbstractFile {

	protected SmbFile file;
    protected String absPath;

	/** File separator is '/' for urls */
	private String separator = "/";
	
	private String name = null;
	private long date = -1;
	private long size = -1;

//	// Indicates whether or not the value has already been retrieved
//	private boolean parentValCached = false;
		
	private boolean isFolder;
	// Indicates whether or not the value has already been retrieved
	private boolean isFolderValCached = false;
	
	private boolean isHidden;
	// Indicates whether or not the value has already been retrieved
	private boolean isHiddenValCached = false;

	
	/**
	 * Creates a new instance of SMBFile.
	 */
	 public SMBFile(String fileURL) {
	 	AuthInfo urlAuthInfo = SMBFile.getAuthInfo(fileURL);
	 	// if the URL specifies a login and password (typed in by the user)
	 	// add it to AuthManager and use it
	 	if (urlAuthInfo!=null) {
	 		AuthManager.put(getPrivateURL(fileURL), urlAuthInfo);
	 	}
	 	// if not, checks if AuthManager has a login/password matching this url
	 	else {
	 		AuthInfo authInfo = AuthManager.get(fileURL);
	 		
	 		if (authInfo!=null) {
	 			// Adds login and password to the URL
	 			fileURL = getPrivateURL(fileURL, authInfo);
	 		}
	 	}
	 	
// System.out.println("fileURL " + fileURL);
	 	// Unlike java.io.File, SmbFile throws an SmbException
	 	// when file doesn't exist
	 	try {
	 		file = new SmbFile(fileURL);

	 		this.absPath = file.getCanonicalPath();
	 		// removes the ending separator character (if any)
	 		this.absPath = absPath.endsWith(separator)?absPath.substring(0,absPath.length()-1):absPath;
	 		// removes login and password from canonical path
	 		absPath = getPrivateURL(absPath);
	 	}
	 	catch(IOException e) {
	 		// Remove newly created AuthInfo entry from AuthManager
	 		if(urlAuthInfo!=null)
	 			AuthManager.remove(getPrivateURL(fileURL));
			
			// File doesn't exist, sets default values
			this.absPath = getPrivateURL(fileURL);			
			this.absPath = absPath.endsWith(separator)?absPath.substring(0,absPath.length()-1):absPath;
			this.isFolder = false;
			int pos = absPath.lastIndexOf('/');
			this.name = pos==-1?"":absPath.substring(pos+1, absPath.length());
			this.date = 0;
			this.size = 0;
			this.isHidden = false;
		}
	 }

	
	/**
	 * Removes login and password information (if any) from the URL.
	 */
	private static String getPrivateURL(String url) {
		String shortURL = "smb://";

		int pos = url.indexOf('@', 6);
		if(pos==-1)
			return url;
		
		shortURL += url.substring(pos+1, url.length());
		return shortURL;			
	}

	/** 
	 * Adds login and password to the URL.
	 */
	private static String getPrivateURL(String url, AuthInfo authInfo) {
		return getPrivateURL(url, authInfo.getLogin(), authInfo.getPassword());
	}

	/** 
	 * Adds login and password to the URL.
	 */
	public static String getPrivateURL(String url, String login, String password) {
		String fullURL = "smb://";
		
		if (!login.trim().equals(""))
			fullURL += login+":"+password+"@";

		if(url.length()>6)
			fullURL += url.substring(6, url.length());

		return fullURL;
	}


	/** 
	 * Returns the login and password information contained in this url, <code>null</code> if
	 * there is none.
	 */
	private static AuthInfo getAuthInfo(String url) {
		String login = "";
		String password = "";

		int pos = url.indexOf('@', 6);
		if (pos==-1) {
			return null;
		}

		int pos2 = url.indexOf(':', 6);
		if (pos2!=-1 && pos2<pos) {
			login = url.substring(6, pos2);
			password = url.substring(pos2+1, pos);
		}
		else {
			login = url.substring(6, pos);
		}

		return new AuthInfo(login, password);
	} 



	public String getName() {
		// Retrieves name and caches it
		if (name==null && file!=null) {
			this.name = file.getParent()==null?absPath+separator:file.getName();
		}

		return name;
	}

	/**
	 * Returns a String representation of this AbstractFile which is the name as returned by getName().
	 */
	public String toString() {
		return getName();
	}
	
	public String getAbsolutePath() {
		return absPath;
	}

	public String getSeparator() {
		return separator;
	}

	public long getDate() {
		// Retrieves date and caches it
		if (date==-1 && file!=null)
			try {
				date = file.lastModified();
			}
			catch(SmbException e) {
				date = 0;
			}
		
		return date;
	}
	
	public long getSize() {
		// Retrieves size and caches it
		if(size==-1 && file!=null)
			try {
				size = file.length();
			}
			catch(SmbException e) {
				size = 0;
			}

		return size;
	}
	
	public AbstractFile getParent() {
		if(file==null)
			return null;
		
		String parent = file.getParent();
        // SmbFile.getParent() never returns null
		if(parent.equals("smb://"))
            return null;
        
		return new SMBFile(parent);
	}
	
	public boolean exists() {
		// Unlike java.io.File, SmbFile.exists() can throw an SmbException
		try {
			return file==null?false:file.exists();
		}
		catch(SmbException e) {
			return false;
		}
	}
	
	public boolean canRead() {
		// Unlike java.io.File, SmbFile.canRead() can throw an SmbException
		try {
			return file==null?false:file.canRead();
		}
		catch(SmbException e) {
			return false;
		}
	}
	
	public boolean canWrite() {
		// Unlike java.io.File, SmbFile.canWrite() can throw an SmbException
		try {
			return file==null?false:file.canWrite();
		}
		catch(SmbException e) {
			return false;
		}
	}
	
	public boolean isHidden() {
		// Retrieves isHidden info and caches it
		if (!isHiddenValCached && file!=null) {
			try {
				isHidden = file.isHidden();
				isHiddenValCached = true;
			}
			catch(SmbException e) {
				isHidden = false;
				isHiddenValCached = true;
			}			
		}
		return isHidden;
	}

	public boolean isFolder() {
		// Retrieves isFolder info and caches it
		if (!isFolderValCached && file!=null) {
			try {
				isFolder = file.isDirectory();
				isFolderValCached = true;
			}
			catch(SmbException e) {
				isFolder = false;
				isFolderValCached = true;
			}
		}
		return isFolder;
	}
	
	
	public boolean equals(AbstractFile f) {
		if(!(f instanceof SMBFile))
			return super.equals(f);		// could be equal to a ZipArchiveFile
		
		// SmbFile's equals method is just perfect: compares canonical paths
		// and IP addresses
		return file.equals(((SMBFile)f).file);
	}
	
	
	
	private String getPrivateURL() {
		String fileURL = absPath;

		AuthInfo authInfo = AuthManager.get(fileURL);
		if (authInfo!=null) {
			// Adds login and password to the URL
			fileURL = getPrivateURL(fileURL, authInfo);
		}
	
		return fileURL;
	}

	public InputStream getInputStream() throws IOException {
		return new SmbFileInputStream(getPrivateURL());
	}
	
	public OutputStream getOutputStream(boolean append) throws IOException {
		return new SmbFileOutputStream(getPrivateURL(), append);
	}
		
	public boolean moveTo(AbstractFile dest) throws IOException  {
		if (dest instanceof SMBFile) 
			try{
				file.renameTo(new SmbFile(dest.getAbsolutePath()));
				return true;
			}
			catch(SmbException e) {
				return false;
			}
		return false;
	}

	public void delete() throws IOException {
		try{
			file.delete();
		}
		catch(SmbException e) {
			throw new IOException();
		}
	}

	public AbstractFile[] ls() throws IOException {
        String names[] = file.list();
		
        if(names==null)
            throw new IOException();
        
        AbstractFile children[] = new AbstractFile[names.length];
		for(int i=0; i<names.length; i++)
			children[i] = AbstractFile.getAbstractFile(absPath+separator+names[i]);

		return children;
	}

	public void mkdir(String name) throws IOException {
		new SmbFile(getPrivateURL()+separator+name).mkdir();
	}
}