package org.apache.manifoldcf.crawler;

import org.apache.manifoldcf.core.InitializationCommand;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.system.ACF;

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
      org.apache.manifoldcf.agents.system.ACF.getMasterDatabaseName(),
      org.apache.manifoldcf.agents.system.ACF.getMasterDatabaseUsername(),
      org.apache.manifoldcf.agents.system.ACF.getMasterDatabasePassword());

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
