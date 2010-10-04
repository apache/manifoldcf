package org.apache.manifoldcf.agents;

import org.apache.manifoldcf.agents.system.ManifoldCF;
import org.apache.manifoldcf.core.InitializationCommand;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.ThreadContextFactory;

/**
 * Parent class for most Initialization commands that are related to Agents
 */
public abstract class BaseAgentsInitializationCommand implements InitializationCommand
{
  public void execute() throws ManifoldCFException
  {
    ManifoldCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    doExecute(tc);
  }

  protected abstract void doExecute(IThreadContext tc) throws ManifoldCFException;
}
