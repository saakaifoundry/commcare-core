package org.commcare.xml;

import org.commcare.suite.model.DisplayUnit;
import org.commcare.suite.model.Text;
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Element parser extended with parsing function(s) that create CommCare
 * specific model objects.
 *
 * @author Phillip Mates
 */
public abstract class CommCareElementParser<T> extends ElementParser<T> {

    /**
     * This requirement may be ignorable, but the user should be prompted *
     */
    public static int SEVERITY_PROMPT = 1;
    /**
     * Something is missing from the environment, but it should be able to be provided *
     */
    public static int SEVERITY_ENVIRONMENT = 2;
    /**
     * It isn't clear how to correct the problem, and probably is a programmer error *
     */
    public static int SEVERITY_UNKOWN = 4;
    /**
     * The profile is incompatible with the major version of the current CommCare installation *
     */
    public static int REQUIREMENT_MAJOR_APP_VERSION = 1;
    /**
     * The profile is incompatible with the minor version of the current CommCare installation *
     */
    public static int REQUIREMENT_MINOR_APP_VERSION = 2;

    public CommCareElementParser(KXmlParser parser) {
        super(parser);
    }

    /**
     * Build a DisplayUnit object by parsing the contents of a display tag.
     */
    public DisplayUnit parseDisplayBlock() throws InvalidStructureException, IOException, XmlPullParserException {
        String imageValue = null;
        String audioValue = null;
        Text displayText = null;

        while (nextTagInBlock("display")) {
            if (parser.getName().equals("text")) {
                displayText = new TextParser(parser).parse();
            }
            //check and parse media stuff
            else if (parser.getName().equals("media")) {
                imageValue = parser.getAttributeValue(null, "image");
                audioValue = parser.getAttributeValue(null, "audio");
                //only ends up grabbing the last entries with
                //each attribute, but we can only use one of each anyway.
            }
        }

        return new DisplayUnit(displayText, imageValue, audioValue);
    }
}