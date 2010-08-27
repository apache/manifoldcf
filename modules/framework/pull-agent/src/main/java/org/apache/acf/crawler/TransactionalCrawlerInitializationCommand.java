package org.apache.acf.crawler;

import org.apache.acf.core.InitializationCommand;
import org.apache.acf.core.interfaces.*;
import org.apache.acf.crawler.system.ACF;

/**
 * @author Jettro Coenradie
 */
public abstract class TransactionalCrawlerInitializationCommand implements InitializationCommand
{
  public void execute() throws ACFException
  {
    ACF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    IDBInterface database = DBInterfaceFactory.make(tc,
      org.apache.acf.agents.system.ACF.getMasterDatabaseName(),
      org.apache.acf.agents.system.ACF.getMasterDatabaseUsername(),
      org.apache.acf.agents.system.ACF.getMasterDatabasePassword());

    try
    {
      database.beginTransaction();
      doExecute(tc);
    }
    catch (ACFException e)
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

  protected abstract void doExecute(IThreadContext tc) throws ACFException;

}
