package org.javarosa.core.model.data;

import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.util.DataUtil;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

/**
 * A response to a question requesting a selection of
 * any number of items from a list.
 *
 * @author Drew Roos
 */
public class SelectMultiData implements IAnswerData {
    Vector<Selection> vs; //vector of Selection

    /**
     * Empty Constructor, necessary for dynamic construction during deserialization.
     * Shouldn't be used otherwise.
     */
    public SelectMultiData() {

    }

    public SelectMultiData(Vector vs) {
        setValue(vs);
    }

    public IAnswerData clone() {
        Vector v = new Vector();
        for (int i = 0; i < vs.size(); i++) {
            v.addElement(vs.elementAt(i).clone());
        }
        return new SelectMultiData(v);
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.core.model.data.IAnswerData#setValue(java.lang.Object)
     */
    public void setValue(Object o) {
        if (o == null) {
            throw new NullPointerException("Attempt to set an IAnswerData class to null.");
        }

        vs = vectorCopy((Vector)o);
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.core.model.data.IAnswerData#getValue()
     */
    public Vector<Selection> getValue() {
        return vectorCopy(vs);
    }

    /**
     * @return A type checked vector containing all of the elements
     * contained in the vector input
     * TODO: move to utility class
     */
    private Vector vectorCopy(Vector input) {
        Vector output = new Vector();
        //validate type
        for (int i = 0; i < input.size(); i++) {
            Selection s = (Selection)input.elementAt(i);
            output.addElement(s);
        }
        return output;
    }

    /**
     * @return THE XMLVALUE!!
     */
    /*
     * (non-Javadoc)
     * @see org.javarosa.core.model.data.IAnswerData#getDisplayText()
     */
    public String getDisplayText() {
        String str = "";

        for (int i = 0; i < vs.size(); i++) {
            Selection s = vs.elementAt(i);
            str += s.getValue();
            if (i < vs.size() - 1)
                str += ", ";
        }

        return str;
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.storage.utilities.Externalizable#readExternal(java.io.DataInputStream)
     */
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        vs = (Vector)ExtUtil.read(in, new ExtWrapList(Selection.class), pf);
    }

    /* (non-Javadoc)
     * @see org.javarosa.core.services.storage.utilities.Externalizable#writeExternal(java.io.DataOutputStream)
     */
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.write(out, new ExtWrapList(vs));
    }

    public UncastData uncast() {
        Enumeration en = vs.elements();
        StringBuffer selectString = new StringBuffer();

        while (en.hasMoreElements()) {
            Selection selection = (Selection)en.nextElement();
            if (selectString.length() > 0)
                selectString.append(" ");
            selectString.append(selection.getValue());
        }
        //As Crazy, and stupid, as it sounds, this is the XForms specification
        //for storing multiple selections.
        return new UncastData(selectString.toString());
    }

    public SelectMultiData cast(UncastData data) throws IllegalArgumentException {
        Vector v = new Vector();
        String[] choices = DataUtil.splitOnSpaces(data.value);
        for (String s : choices) {
            v.addElement(new Selection(s));
        }
        return new SelectMultiData(v);
    }

    public boolean isInSelection(String value) {
        for(Selection s : vs) {
            if(s.getValue().equals(value)) {return true;}
        }
        return false;
    }
}
