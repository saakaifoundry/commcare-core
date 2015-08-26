package org.commcare.util.test;

import org.commcare.api.persistence.UserSqlSandbox;
import org.commcare.api.persistence.SqlSandboxUtils;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.model.Case;
import org.commcare.core.parse.ParseUtils;
import org.javarosa.core.model.User;
import org.commcare.test.utils.SqlTestUtils;
import org.javarosa.core.model.instance.FormInstance;
import org.junit.Before;
import org.junit.Test;

import java.util.Vector;

/**
 * Tests for the SqlSandbox API. Just initializes and makes sure we can access at the moment.
 *
 * @author wspride
 */
public class UserSqlSandboxTest {

    private UserSqlSandbox sandbox;
    private Vector<String> owners;
    String username = "sandbox-test-user";

    @Before
    public void setUp() throws Exception {
        SqlTestUtils.deleteDatabase(username);
        sandbox = SqlSandboxUtils.getStaticStorage(username);
        ParseUtils.parseIntoSandbox(this.getClass().getClassLoader().getResourceAsStream("ipm_restore.xml"), sandbox);
        sandbox = null;
    }

    @Test
    public void test() {
        sandbox = SqlSandboxUtils.getStaticStorage(username);
        Case readCase = sandbox.getCaseStorage().read(1);
        Ledger readLedger = sandbox.getLedgerStorage().read(1);
        FormInstance readFixture = sandbox.getUserFixtureStorage().read(1);
        User readUser = sandbox.getUserStorage().read(1);
    }
}
