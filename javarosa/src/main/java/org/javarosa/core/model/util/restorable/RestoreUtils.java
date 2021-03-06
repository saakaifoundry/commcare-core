package org.javarosa.core.model.util.restorable;

import org.javarosa.core.model.Constants;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IConditionExpr;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.XPathConditional;
import org.javarosa.xpath.expr.XPathPathExpr;

import java.util.Date;
import java.util.Vector;

public class RestoreUtils {
    public static final String RECORD_ID_TAG = "rec-id";

    public static TreeReference ref(String refStr) {
        return FormInstance.unpackReference(new XPathReference(refStr));
    }

    public static IConditionExpr refToPathExpr(TreeReference ref) {
        return new XPathConditional(XPathPathExpr.fromRef(ref));
    }

    private static TreeReference topRef(FormInstance dm) {
        return ref("/" + dm.getRoot().getName());
    }

    private static TreeReference childRef(String childPath, TreeReference parentRef) {
        return ref(childPath).parent(parentRef);
    }

    //used for incoming data
    private static int getDataType(Class c) {
        int dataType;
        if (c == String.class) {
            dataType = Constants.DATATYPE_TEXT;
        } else if (c == Integer.class) {
            dataType = Constants.DATATYPE_INTEGER;
        } else if (c == Long.class) {
            dataType = Constants.DATATYPE_LONG;
        } else if (c == Float.class || c == Double.class) {
            dataType = Constants.DATATYPE_DECIMAL;
        } else if (c == Date.class) {
            dataType = Constants.DATATYPE_DATE;
            //Clayton Sims - Jun 16, 2009 - How are we handling Date v. Time v. DateTime?
        } else if (c == Boolean.class) {
            dataType = Constants.DATATYPE_TEXT; //booleans are serialized as a literal 't'/'f'
        } else {
            throw new RuntimeException("Can't handle data type " + c.getName());
        }

        return dataType;
    }

    @SuppressWarnings("unused")
    public static Object getValue(String xpath, FormInstance tree) {
        TreeReference context = topRef(tree);
        TreeElement node = tree.resolveReference(ref(xpath).contextualize(context));
        if (node == null) {
            throw new RuntimeException("Could not find node [" + xpath + "] when parsing saved instance!");
        }

        if (node.isRelevant()) {
            IAnswerData val = node.getValue();
            return (val == null ? null : val.getValue());
        } else {
            return null;
        }
    }

    public static void applyDataType(FormInstance dm, String path, TreeReference parent, Class type) {
        int dataType = getDataType(type);
        TreeReference ref = childRef(path, parent);

        Vector v = new EvaluationContext(dm).expandReference(ref);
        for (int i = 0; i < v.size(); i++) {
            TreeElement e = dm.resolveReference((TreeReference)v.elementAt(i));
            e.setDataType(dataType);
        }
    }

    public static void templateData(Restorable r, FormInstance dm, TreeReference parent) {
        if (parent == null) {
            parent = topRef(dm);
            applyDataType(dm, "timestamp", parent, Date.class);
        }

        if (r instanceof Persistable) {
            applyDataType(dm, RECORD_ID_TAG, parent, Integer.class);
        }

        r.templateData(dm, parent);
    }
}
