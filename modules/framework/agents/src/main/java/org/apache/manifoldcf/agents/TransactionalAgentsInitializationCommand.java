package org.apache.manifoldcf.agents;

import org.apache.manifoldcf.agents.system.ManifoldCF;
import org.apache.manifoldcf.core.InitializationCommand;
import org.apache.manifoldcf.core.interfaces.*;

/**
 * @author Jettro Coenradie
 */
public abstract class TransactionalAgentsInitializationCommand implements InitializationCommand
{
  public void execute() throws ManifoldCFException
  {
    ManifoldCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    IDBInterface database = DBInterfaceFactory.make(tc,
      org.apache.manifoldcf.agents.system.ManifoldCF.getMasterDatabaseName(),
      org.apache.manifoldcf.agents.system.ManifoldCF.getMasterDatabaseUsername(),
      org.apache.manifoldcf.agents.system.ManifoldCF.getMasterDatabasePassword());

    try
    {
      database.beginTransaction();
      doExecute(tc);
    }
    catch (ManifoldCFException e)
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

  protected abstract void doExecute(IThreadContext tc) throws ManifoldCFException;
}
