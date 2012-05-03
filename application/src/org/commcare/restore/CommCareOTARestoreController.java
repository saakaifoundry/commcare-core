/**
 * 
 */
package org.commcare.restore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Hashtable;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;

import org.commcare.cases.model.Case;
import org.commcare.cases.util.CaseDBUtils;
import org.commcare.core.properties.CommCareProperties;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.model.PeriodicEvent;
import org.commcare.util.CommCareInstanceInitializer;
import org.commcare.util.CommCareTransactionParserFactory;
import org.commcare.util.CommCareUtil;
import org.commcare.util.time.AutoSyncEvent;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.log.WrappedException;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtility;
import org.javarosa.core.services.storage.StorageManager;
import org.javarosa.core.services.storage.StorageModifiedException;
import org.javarosa.core.util.StreamsUtil;
import org.javarosa.j2me.log.CrashHandler;
import org.javarosa.j2me.log.HandledCommandListener;
import org.javarosa.j2me.log.HandledThread;
import org.javarosa.j2me.storage.rms.RMSTransaction;
import org.javarosa.j2me.view.J2MEDisplay;
import org.javarosa.model.xform.DataModelSerializer;
import org.javarosa.service.transport.securehttp.AuthenticatedHttpTransportMessage;
import org.javarosa.service.transport.securehttp.DefaultHttpCredentialProvider;
import org.javarosa.service.transport.securehttp.HttpAuthenticator;
import org.javarosa.services.transport.TransportMessage;
import org.javarosa.services.transport.TransportService;
import org.javarosa.services.transport.impl.TransportException;
import org.javarosa.services.transport.impl.simplehttp.StreamingHTTPMessage;
import org.javarosa.user.model.User;
import org.xmlpull.v1.XmlPullParserException;

/**
 * 
 * TODO: This class is a huge quagmire of interlinking completely coupled method stacks. Rewrite to have clear
 * workflow v. functional chunks
 * @author ctsims
 *
 */
public class CommCareOTARestoreController implements HandledCommandListener {
	
	CommCareOTACredentialEntry entry;
	CommCareOTARestoreView view;
	
	CommCareOTARestoreTransitions transitions;
	
	int authAttempts = 0;
	String restoreURI;
	boolean noPartial;
	boolean isSync;
	int[] caseTallies;
	
	HttpAuthenticator authenticator;
	boolean errorsOccurred;
	String syncToken;
	private boolean recoveryMode = false;
	String originalRestoreURI;
	String logSubmitURI;
	String stateHash;
	
	public CommCareOTARestoreController(CommCareOTARestoreTransitions transitions, String restoreURI) {
		this(transitions, restoreURI, null);
	}
	
	public CommCareOTARestoreController(CommCareOTARestoreTransitions transitions, String restoreURI, HttpAuthenticator authenticator) {
		this(transitions, restoreURI, authenticator, false, false, null, null);
	}
				
	public CommCareOTARestoreController(CommCareOTARestoreTransitions transitions, String restoreURI,
			HttpAuthenticator authenticator, boolean isSync, boolean noPartial, String syncToken, String logSubmitURI) {
		if (isSync && !noPartial) {
			System.err.println("WARNING: no-partial mode is strongly recommended when syncing");
		}
		this.syncToken = syncToken;
		
		this.originalRestoreURI = restoreURI;
		this.authenticator = authenticator;
		this.logSubmitURI = logSubmitURI;
			
		this.isSync = isSync;
		if (isSync) {
			this.stateHash = CaseDBUtils.computeHash((IStorageUtility<Case>)StorageManager.getStorage(Case.STORAGE_KEY));
			initURI(syncToken, stateHash);
		} else {
			initURI(null,null);
		}
		this.noPartial = noPartial;
		
		view = new CommCareOTARestoreView(Localization.get("intro.restore"));
		view.setCommandListener(this);
		
		entry = new CommCareOTACredentialEntry(Localization.get("intro.restore"));
		entry.setCommandListener(this);
		
		this.transitions = transitions;
	}
	
	public void start() {
		Reference bypassRef = getBypassRef();
		if(bypassRef != null) {
			J2MEDisplay.setView(view);
			tryBypass(bypassRef);
		} else{ 
			entry.sendMessage("");
			startOtaProcess();
		}
	}
	
	private void startOtaProcess() {
		 if(authenticator == null) {
			authAttempts = 0;
			getCredentials();
		} else {
			authAttempts = 1;
			J2MEDisplay.setView(view);
			tryDownload(AuthenticatedHttpTransportMessage.AuthenticatedHttpRequest(restoreURI, authenticator));
		}
	}
	
	private void getCredentials() {
		J2MEDisplay.setView(entry);
	}
		
	private void tryDownload(AuthenticatedHttpTransportMessage message) {
		view.addToMessage(Localization.get("restore.message.startdownload"));
		Logger.log("restore", "start");
		try {
			if(message.getUrl() == null) {
				fail(Localization.get("restore.noserveruri"), null, "no restore url");
				J2MEDisplay.setView(view);
				doneFail(null);
				return;
			}
			AuthenticatedHttpTransportMessage sent = (AuthenticatedHttpTransportMessage)TransportService.sendBlocking(message);
			if(sent.isSuccess()) {
				view.addToMessage(Localization.get("restore.message.connectionmade"));
				try {
					downloadRemoteData(sent.getResponse());
					return;
				} catch(IOException e) {
					J2MEDisplay.setView(entry);
					entry.sendMessage(Localization.get("restore.baddownload"));
					doneFail("download failure: " + WrappedException.printException(e));
					return;
				}
			} else {
				view.addToMessage(Localization.get("restore.message.connection.failed"));
				if(sent.getResponseCode() == 401) {
					view.addToMessage(Localization.get("restore.badcredentials"));
					entry.sendMessage(Localization.get("restore.badcredentials"));
					if(authAttempts > 0) {
						Logger.log("restore", "bad credentials; " + authAttempts + " attempts remain");
						authAttempts--;
						getCredentials();
					} else {
						doneFail("bad credentials");
					}
					return;
				} else if(sent.getResponseCode() == 404) {
					entry.sendMessage(Localization.get("restore.badserver"));
					doneFail("404");
					return;
				} else if(sent.getResponseCode() == 412) {
					//Our local copy of the case database has gotten out of sync. We need to start a recovery
					//process.
					entry.sendMessage(Localization.get("restore.bad.db"));
					view.setMessage(Localization.get("restore.bad.db"));
					startRecovery();
					return;
				} else if(sent.getResponseCode() == 503) {
					view.addToMessage("We're still busy loading your cases and follow-ups. Try again in five minutes.");
					entry.sendMessage("We're still busy loading your cases and follow-ups. Try again in five minutes.");
					doneFail("503");
					return;
				} else {
					entry.sendMessage(sent.getFailureReason());
					doneFail("other: " + sent.getFailureReason());
					return;
				}
			}
		} catch (TransportException e) {
			entry.sendMessage(Localization.get("restore.message.connection.failed"));
			doneFail("tx exception: " + WrappedException.printException(e));
		}
	}
	
	/** 
	 * The recovery process comes in three phases. First, reporting to the server all of the cases that
	 * currently live on the phone (so the server can compare to its current state).
	 * 
	 * Next, the full restore data is retrieved from the server and stored locally to ensure that the db
	 * can be recovered. Then local storage is cleared of data, and  
	 */
	private void startRecovery() {
		//Make a streaming message (the db is likely be too big to store in memory) 
		TransportMessage message = new StreamingHTTPMessage(this.getSubmitUrl()) {
			public void _writeBody(OutputStream os) throws IOException {
				//TODO: This is just the casedb, we actually want 
				DataModelSerializer s = new DataModelSerializer(os, new CommCareInstanceInitializer());
				s.serialize(new ExternalDataInstance("jr://instance/casedb/report" + "/" + syncToken + "/" + stateHash,"casedb"), null);
			}
		};
		
		view.addToMessage(Localization.get("restore.recover.send"));
		try {
			message = TransportService.sendBlocking(message);
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(message.isSuccess()) {
			//The server is now informed of our current state, time for the tricky part,
			this.recoveryMode  = true;
			initURI(null, null);
			//TODO: Set a flag somewhere (sync token perhaps) that we're in recovery mode
			this.startOtaProcess();
		} else {
			this.doneFail(Localization.get("restore.recover.fail"));
		}
	}

	private String getSubmitUrl() {
		return logSubmitURI;
	}

	/**
	 * 
	 * @param stream
	 * @throws IOException If there was a problem which resulted in the cached
	 * file being corrupted or unavailable _and_ the input stream becoming invalidated
	 * such that a retry is necessary.
	 */
	private void downloadRemoteData(InputStream stream) throws IOException {
		J2MEDisplay.setView(view);
		Reference ref;
		try {
			ref = ReferenceManager._().DeriveReference(getCacheRef());
			if(ref.isReadOnly()) {
				view.addToMessage(Localization.get("restore.nocache"));
				//TODO: ^ That.
			} else {
				OutputStream output;
				
				//We want to treat any problems dealing with the inability to 
				//download as separate from a _failed_ download, which is 
				//what this try-catch is all about.
				try {
					if(ref.doesBinaryExist()) {
						ref.remove();
					}
					output = ref.getOutputStream();
				}
			    catch (Exception e) {
			    	if(recoveryMode) {
			    		//In recovery mode we can't really afford to not cache this, so report that, and try again.
			    		view.setMessage(Localization.get("restore.recover.needcache"));
			    		return;
			    	} else {
			    		noCache(stream);
			    		return;
			    	}
				}
			    
			    //Now any further IOExceptions will get handled as "download failed", 
			    //rather than "couldn't attempt to download"
				StreamsUtil.writeFromInputToOutput(stream, output);
				//need to close file's write stream before we read from it (S60 is not happy otherwise)
				output.close();
				
				view.addToMessage(Localization.get("restore.downloaded"));
				startRestore(ref.getStream());
			}
		} catch (InvalidReferenceException e) {
			noCache(stream);
		}

	}
	
	private void initURI (String lastSync, String stateHash) {
		
		String baseURI = this.originalRestoreURI;
		if(baseURI.indexOf("verson=2.0") == -1) {
			baseURI = baseURI + (baseURI.indexOf("?") == -1 ? "?" : "&") + "version=2.0";
		}
		
		//get property
		if (lastSync != null) {
			this.restoreURI = baseURI + (baseURI.indexOf("?") == -1 ? "?" : "&" ) + "since=" + lastSync + "&state=ccsh:" + stateHash;
			System.out.println("RestoreURI: "+ restoreURI);
		} else {
			this.restoreURI = baseURI;
			System.out.println("RestoreURI: "+ restoreURI);
		}
	}
	
	private void noCache(InputStream input) throws IOException {
		if (this.noPartial) {
			Logger.log("ota-restore", "attempted to restore OTA in 'no partial' mode, but could not cache payload locally");
			throw new IOException();
		} else {
			view.addToMessage(Localization.get("restore.nocache"));
			startRestore(input);
		}
 	}
	
	public boolean startRestore(InputStream input) {
		J2MEDisplay.setView(view);
		view.addToMessage(Localization.get("restore.starting"));
		
		if(recoveryMode) {
			view.addToMessage(Localization.get("restore.recovery.wipe"));
			//We've downloaded our file and can now recovery state fully for this user, so we need to wipe 
			//out existing cases. Ideally we'd do this by renaming the RMS (so we could recover if needed), 
			//but for now, just go for it.
			StorageManager.getStorage(Case.STORAGE_KEY).removeAll();
		}
		
		errorsOccurred = false;
		
		boolean success = false;
		String[] parseErrors = new String[0];
		String restoreID = null;
		
		try {
			beginTransaction();
			CommCareTransactionParserFactory factory = new CommCareTransactionParserFactory(!noPartial);
			DataModelPullParser parser = new DataModelPullParser(input,factory);
			
			success = parser.parse();
			restoreID = factory.getRestoreId();
			caseTallies = factory.getCaseTallies();
			//TODO: Is success here too strict?
			if (success) {
				transitions.commitSyncToken(restoreID);
				PropertyManager._().setProperty(CommCareProperties.LAST_SYNC_AT, DateUtils.formatDateTime(new Date(), DateUtils.FORMAT_ISO8601));
			}
			parseErrors = parser.getParseErrors();
			
		} catch (IOException e) {
			fail(Localization.get("restore.fail"), e, null);
			return false;
		} catch (InvalidStructureException e) {
			fail(Localization.get("restore.fail"), e, null);
			return false;
		} catch (XmlPullParserException e) {
			fail(Localization.get("restore.fail"), e, null);
			return false;
		} catch (UnfullfilledRequirementsException e) {
			fail(Localization.get("restore.fail"), e, null);
			return false;
		} catch (RuntimeException e) {
			success = false;
		} finally {
			if (success) {
				commitTransaction();
			} else {
				rollbackTransaction();
			}
		}
		
		if (success) {
			view.addToMessage(Localization.get("restore.success"));
			Logger.log("restore", "successful: " + (restoreID != null ? restoreID : "???"));
		} else {
			if (noPartial) {
				view.addToMessage(Localization.get("restore.fail"));				
			} else {
				view.addToMessage(Localization.get("restore.success.partial") + " " + parseErrors.length);
			}
			
			Logger.log("restore", (noPartial ? "restore errors; rolled-back" : "unsuccessful or partially successful") +
					": " + (restoreID != null ? restoreID : "???"));
			for(String s : parseErrors) {
				Logger.log("restore", "err: " + s);
			}
						
			errorsOccurred = true;
		}
		done();
		return success || !noPartial;
	}
	
	private void beginTransaction () {
		if (this.noPartial)
			RMSTransaction.beginTransaction();
	}	
				
	private void commitTransaction () {
		if (this.noPartial)
			RMSTransaction.commitTransaction();
	}
			
	private void rollbackTransaction () {
		if (this.noPartial)
			RMSTransaction.rollbackTransaction();
	}
	
	private void done() {
		view.setFinished();
		view.addToMessage(Localization.get("restore.key.continue"));
	}
	
	private void doneFail(String msg) {
		if (msg != null) {
			Logger.log("restore", "fatal error: " + msg);
		}
		if (this.isSync) {
			done();
			errorsOccurred = true;
		}
	}
	
	private void fail(String message, Exception e, String logmsg) {
		view.addToMessage(message);
		
		if (logmsg == null) {
			logmsg = (e != null ? WrappedException.printException(e) : "no message");
		}
		Logger.log("restore", "fatal error: " + logmsg);
		
		if (e != null) {
			e.printStackTrace();
		}
		
		//Retry/Cancel from scratch or by 
	}

	protected String getCacheRef() {
		return "jr://file/commcare_ota_backup.xml";
	}
	
	private AuthenticatedHttpTransportMessage getClientMessage() {
		AuthenticatedHttpTransportMessage message = AuthenticatedHttpTransportMessage.AuthenticatedHttpRequest(restoreURI, 
				new HttpAuthenticator(CommCareUtil.wrapCredentialProvider(new DefaultHttpCredentialProvider(entry.getUsername(), entry.getPassword())), false));
		return message;
	}

	public void _commandAction(Command c, Displayable d) {
		if(c.equals(CommCareOTACredentialEntry.DOWNLOAD)) {
			if(userExists(entry.getUsername()) && !isSync) {
				entry.sendMessage(Localization.get("restore.user.exists"));
				return;
			}
			
			tryDownload(getClientMessage());
		} else if(d == entry && c.equals(CommCareOTACredentialEntry.CANCEL)) {
			transitions.cancel();
		} else if(c.equals(view.FINISHED)) {
			PeriodicEvent.markTriggered(new AutoSyncEvent());
			transitions.done(errorsOccurred);
		}
	}
	
	private boolean userExists(String username) {
		int attempts = 0;
		//An absurd number of tries
		while(attempts < 50) {
			try{
				IStorageIterator iterator = StorageManager.getStorage(User.STORAGE_KEY).iterate();
				while(iterator.hasMore()) {
					User u = (User)iterator.nextRecord();
					if(username.toLowerCase().equals(u.getUsername().toLowerCase())) {
						return true;
					}
				}
				return false;
			}
			catch(StorageModifiedException sme) {
				//storage modified while we were going through users. Try again
				attempts++;
			}
		}
		//Dunno what to do here, really, it would be crazy to gt to this point.
		//Maybe should throw an exception, actually.
		Logger.log("restore", "Could not look through User list to determine if user " + username + " exists.");
		return false;
	}

	public void commandAction(Command c, Displayable d) {
		CrashHandler.commandAction(this,c,d);
	}
	
	/**
	 * 
	 * @return Null if the bypass file doesn't exist or couldn't be resolved. A Reference to the bypass
	 * file if one appears to exist.
	 */
	protected Reference getBypassRef() {
		try {
			String bypassRef = PropertyManager._().getSingularProperty(CommCareProperties.OTA_RESTORE_OFFLINE);
			if(bypassRef == null || bypassRef == "") {
				return null;
			}
		
			Reference bypass = ReferenceManager._().DeriveReference(bypassRef);
			if(bypass == null || !bypass.doesBinaryExist()) {
				return null;
			}
			return bypass;
		} catch(Exception e){
			e.printStackTrace();
			//It would be absurdly stupid if we couldn't OTA restore because of an error here
			return null;
		}
		
	}
	
	public void tryBypass(final Reference bypass) {
		//Need to launch the bypass attempt in a thread.
		
		HandledThread t = new HandledThread() {
			public void _run() {
				view.addToMessage(Localization.get("restore.bypass.start", new String [] {bypass.getLocalURI()}));
				
				try {
					Logger.log("restore", "starting bypass restore attempt with file: " + bypass.getLocalURI());
					InputStream restoreStream = bypass.getStream();
					if(startRestore(restoreStream)) {
						try {
							//Success! Try to wipe the local file and then let the UI handle the rest.
							restoreStream.close();
							if(!bypass.isReadOnly()) {
								view.addToMessage(Localization.get("restore.bypass.clean"));
								bypass.remove();
								view.addToMessage(Localization.get("restore.bypass.clean.success"));
							}
						} catch (IOException e) {
							//Even if we fail to delete the local file, it's mostly fine. Jut let the user know
							e.printStackTrace();
							view.addToMessage(Localization.get("restore.bypass.cleanfail", new String[] {bypass.getLocalURI()}));
						}
						Logger.log("restore", "bypass restore succeeded");
						return;
					}
				} catch(IOException e) {
					//Couldn't open a stream to the restore file, we'll need to dump out to
					//OTA
					e.printStackTrace();
				}
				
				//Something bad about the restore file. 
				//Skip it and dump back to OTA Restore
				
				Logger.log("restore", "bypass restore failed, falling back to OTA");
				view.addToMessage(Localization.get("restore.bypass.fail"));
				
				entry.sendMessage(Localization.get("restore.bypass.instructions"));
				startOtaProcess();
			}
		};
		t.start();
	}
	
	public Hashtable<String, Integer> getCaseTallies() {
		Hashtable<String, Integer> tall = new Hashtable<String, Integer>();
		tall.put("create", new Integer(this.caseTallies[0]));
		tall.put("update", new Integer(this.caseTallies[1]));
		tall.put("close", new Integer(this.caseTallies[2]));
		return tall;
	}
}
