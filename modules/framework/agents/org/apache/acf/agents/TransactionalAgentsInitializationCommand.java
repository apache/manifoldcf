package org.apache.acf.agents;

import org.apache.acf.agents.system.LCF;
import org.apache.acf.core.InitializationCommand;
import org.apache.acf.core.interfaces.*;

/**
 * @author Jettro Coenradie
 */
public abstract class TransactionalAgentsInitializationCommand implements InitializationCommand
{
  public void execute() throws LCFException
  {
    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    IDBInterface database = DBInterfaceFactory.make(tc,
      org.apache.acf.agents.system.LCF.getMasterDatabaseName(),
      org.apache.acf.agents.system.LCF.getMasterDatabaseUsername(),
      org.apache.acf.agents.system.LCF.getMasterDatabasePassword());

    try
    {
      database.beginTransaction();
      doExecute(tc);
    }
    catch (LCFException e)
    {
      database.signalRollback();
      throw e;
    }
    catch (Error e)
    {
      database.signalRollback();
      throw e;
    }
    finally
    {
      database.endTransaction();
    }

  }

  protected abstract void doExecute(IThreadContext tc) throws LCFException;
}
