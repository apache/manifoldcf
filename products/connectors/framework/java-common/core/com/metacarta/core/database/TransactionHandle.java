/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
* 
* http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.metacarta.core.database;

import com.metacarta.core.interfaces.*;

import java.util.*;

/** This class represents all the data that describes a transaction, including:
* (2) the previous transaction handle,
* (3) the unique transaction id,
* (4) the cache keys that have been invalidated within this transaction.
*/
public class TransactionHandle
{
	public static final String _rcsid = "@(#)$Id$";

	protected TransactionHandle previousTransaction;
	protected String transactionID;
	protected int transactionType;

	public TransactionHandle(TransactionHandle previousTransaction, int transactionType)
		throws MetacartaException
	{
		// Grab a unique ID
		transactionID = IDFactory.make();
		this.previousTransaction = previousTransaction;
		this.transactionType = transactionType;
	}

	public String getTransactionID()
	{
		return transactionID;
	}

	public TransactionHandle getParent()
	{
		return previousTransaction;
	}

	public int getTransactionType()
	{
		return transactionType;
	}
}

