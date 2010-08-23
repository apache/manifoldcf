package org.apache.acf.core;

import org.apache.acf.core.interfaces.LCFException;

/**
 * Interface for commands that initialize state of the connector framework. Among implementations available are:
 * - Database creation
 * - Registrations of agent
 * - Registrations of connectors
 *
 * @author Jettro Coenradie
 */
public interface InitializationCommand
{
  /**
   * Execute the command.
   *
   * @throws LCFException Thrown if the execution fails
   */
  void execute() throws LCFException;
}
