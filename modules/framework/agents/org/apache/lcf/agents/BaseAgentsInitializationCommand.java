package org.apache.lcf.agents;

import org.apache.lcf.agents.system.LCF;
import org.apache.lcf.core.InitializationCommand;
import org.apache.lcf.core.interfaces.IThreadContext;
import org.apache.lcf.core.interfaces.LCFException;
import org.apache.lcf.core.interfaces.ThreadContextFactory;

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
