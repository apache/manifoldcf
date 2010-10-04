package org.apache.manifoldcf.crawler;

import org.apache.manifoldcf.core.InitializationCommand;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.ThreadContextFactory;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

/**
 * @author Jettro Coenradie
 */
public abstract class BaseCrawlerInitializationCommand implements InitializationCommand
{
  public void execute() throws ManifoldCFException
  {
    ManifoldCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    doExecute(tc);
  }

  protected abstract void doExecute(IThreadContext tc) throws ManifoldCFException;

}
