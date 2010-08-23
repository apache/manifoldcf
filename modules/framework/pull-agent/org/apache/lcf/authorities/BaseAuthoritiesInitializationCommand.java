package org.apache.lcf.authorities;

import org.apache.lcf.authorities.interfaces.AuthorityConnectorManagerFactory;
import org.apache.lcf.authorities.interfaces.IAuthorityConnectorManager;
import org.apache.lcf.authorities.system.LCF;
import org.apache.lcf.core.InitializationCommand;
import org.apache.lcf.core.interfaces.IThreadContext;
import org.apache.lcf.core.interfaces.LCFException;
import org.apache.lcf.core.interfaces.ThreadContextFactory;

/**
 * @author Jettro Coenradie
 */
public abstract class BaseAuthoritiesInitializationCommand implements InitializationCommand
{
  public void execute() throws LCFException
  {
    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    IAuthorityConnectorManager mgr = AuthorityConnectorManagerFactory.make(tc);

    doExecute(mgr);
  }

  protected abstract void doExecute(IAuthorityConnectorManager mgr) throws LCFException;
}
