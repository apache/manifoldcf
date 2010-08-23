package org.apache.acf.agents;

import org.apache.acf.agents.system.LCF;
import org.apache.acf.core.InitializationCommand;
import org.apache.acf.core.interfaces.IThreadContext;
import org.apache.acf.core.interfaces.LCFException;
import org.apache.acf.core.interfaces.ThreadContextFactory;

/**
 * Parent class for most Initialization commands that are related to Agents
 */
public abstract class BaseAgentsInitializationCommand implements InitializationCommand
{
  public void execute() throws LCFException
  {
    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    doExecute(tc);
  }

  protected abstract void doExecute(IThreadContext tc) throws LCFException;
}
