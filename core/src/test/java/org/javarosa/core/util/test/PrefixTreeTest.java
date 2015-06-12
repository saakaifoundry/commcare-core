package org.javarosa.core.util.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Vector;

import org.javarosa.core.model.test.QuestionDefTest;
import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.PrefixTreeNode;

public class PrefixTreeTest extends TestCase {

    public PrefixTreeTest(String name) {
        super(name);
    }

    public PrefixTreeTest() {
        super();
    }

    public Test suite() {
        TestSuite aSuite = new TestSuite();

        suite.addTest(new PrefixTreeTest("testBasic"));
        suite.addTest(new PrefixTreeTest("testHeuristic"));

        return aSuite;
    }


    private int[] prefixLengths = new int[]{0, 1, 2, 10, 50};


    public void add(PrefixTree t, String s) {
        PrefixTreeNode node = t.addString(s);
        //System.out.println(t.toString());

        if (!node.render().equals(s)) {
            fail("Prefix tree mangled: " + s + " into " + node.render());
        }

        Vector v = t.getStrings();
        for (int i = 0; i < v.size(); i++) {
            //System.out.println((String)v.elementAt(i));
        }
    }

    public void testBasic() {
        for (int i : prefixLengths) {
            PrefixTree t = new PrefixTree(i);

            add(t, "abcde");
            add(t, "abcdefghij");
            add(t, "abcdefghijklmno");
            add(t, "abcde");
            add(t, "abcdefg");
            add(t, "xyz");
            add(t, "abcdexyz");
            add(t, "abcppppp");
            System.out.println(t.toString());
        }

    }

    public void testHeuristic() {
        for (int i : prefixLengths) {

            PrefixTree t = new PrefixTree(i);

            add(t, "jr://file/images/something/abcd.png");
            add(t, "jr://file/audio/something/abcd.mp3");
            add(t, "jr://file/audio/something/adfd.mp3");
            add(t, "jr://file/images/something/dsf.png");
            add(t, "jr://file/images/sooth/abcd.png");
            add(t, "jr://file/audio/something/bsadf.mp3");
            add(t, "jr://file/audio/something/fsde.mp3");

            add(t, "jr://file/images/some");
            System.out.println(t.toString());
        }
    }
}
