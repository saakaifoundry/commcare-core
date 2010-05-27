/**
 * 
 */
package org.commcare.applogic;

import org.commcare.api.transitions.FirstStartupTransitions;
import org.commcare.core.properties.CommCareProperties;
import org.commcare.util.CommCareContext;
import org.commcare.view.FirstStartupView;
import org.javarosa.core.api.State;
import org.javarosa.core.services.PropertyManager;
import org.javarosa.j2me.view.J2MEDisplay;

/**
 * @author ctsims
 *
 */
public class CommCareFirstStartState implements State, FirstStartupTransitions{

	FirstStartupView view;
	
	public CommCareFirstStartState() {
		view = new FirstStartupView(this);
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.api.State#start()
	 */
	public void start() {
		J2MEDisplay.setView(view);
	}

	public void exit() {
		CommCareContext._().exitApp();
	}

	public void login() {
		PropertyManager._().setProperty(CommCareProperties.IS_FIRST_RUN, CommCareProperties.FIRST_RUN_YES);
		J2MEDisplay.startStateWithLoadingScreen(new CommCareLoginState());
	}

	public void restore() {
		J2MEDisplay.startStateWithLoadingScreen(new CommCareOTARestoreState() {

			public void cancel() {
				J2MEDisplay.startStateWithLoadingScreen(new CommCareFirstStartState());
			}

			public void done() {
				PropertyManager._().setProperty(CommCareProperties.IS_FIRST_RUN, CommCareProperties.FIRST_RUN_NO);
				J2MEDisplay.startStateWithLoadingScreen(new CommCareLoginState());
			}
			
		});
	}

}
