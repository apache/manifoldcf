package org.apache.lcf.crawler;

import org.apache.lcf.core.InitializationCommand;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.crawler.system.LCF;

/**
 * @author Jettro Coenradie
 */
public abstract class TransactionalCrawlerInitializationCommand implements InitializationCommand
{
  public void execute() throws LCFException
  {
    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    IDBInterface database = DBInterfaceFactory.make(tc,
      org.apache.lcf.agents.system.LCF.getMasterDatabaseName(),
      org.apache.lcf.agents.system.LCF.getMasterDatabaseUsername(),
      org.apache.lcf.agents.system.LCF.getMasterDatabasePassword());

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
