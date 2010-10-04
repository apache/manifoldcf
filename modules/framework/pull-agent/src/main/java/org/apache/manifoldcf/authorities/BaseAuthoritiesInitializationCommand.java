package org.apache.manifoldcf.authorities;

import org.apache.manifoldcf.authorities.interfaces.AuthorityConnectorManagerFactory;
import org.apache.manifoldcf.authorities.interfaces.IAuthorityConnectorManager;
import org.apache.manifoldcf.authorities.system.ACF;
import org.apache.manifoldcf.core.InitializationCommand;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ACFException;
import org.apache.manifoldcf.core.interfaces.ThreadContextFactory;

/**
 * @author Jettro Coenradie
 */
public abstract class BaseAuthoritiesInitializationCommand implements InitializationCommand
{
  public void execute() throws ACFException
  {
    ACF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    IAuthorityConnectorManager mgr = AuthorityConnectorManagerFactory.make(tc);

    doExecute(mgr);
  }

  protected abstract void doExecute(IAuthorityConnectorManager mgr) throws ACFException;
}
