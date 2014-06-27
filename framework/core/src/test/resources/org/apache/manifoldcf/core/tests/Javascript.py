#!/usr/bin/python
# $Id$

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import re

def build_method_name( name, argset ):
    """ Method names consist of the actual name plus the number of arguments """
    return "%s:%d" % (name,len(argset))

# Basic class which represents a javascript object.
# Each such class instance has a virtual set of properties, each
# of which is an object in its own right.
class JSObject:

    def __init__( self ):
        pass

    def is_type( self, type ):
        return isinstance( self, type )

    def call( self, argset, context ):
        # If this is a method, evaluate it; otherwise it's
        # an error
        raise Exception("Attempt to evaluate a non-method object: %s" % unicode(self))

    def construct( self, argset, context ):
        # If this is a class, construct it, otherwise it's
        # an error
        raise Exception("Attempt to construct a non-class object: %s" % unicode(self))

    def get_type( self, member_name ):
        return JSString( "undefined" )

    def get_value( self, member_name ):
        raise Exception("Object %s has no such property '%s'" % (unicode(self), member_name) )

    def set_value( self, member_name, value ):
        raise Exception("Object %s has no such property '%s'" % (unicode(self), member_name) )

    def type_value( self ):
        raise Exception("Object %s has no type value" % unicode(self) )

    def str_value( self ):
        raise Exception("Object %s has no string value" % unicode(self) )

    def num_value( self ):
        raise Exception("Object %s has no numeric value" % unicode(self) )

    def bool_value( self ):
        raise Exception("Object %s has no boolean value" % unicode(self) )

    def set_reference( self, newobject ):
        # For objects that are references, this is how they are set.  All other kinds of objects
        # do not support this operation.
        raise Exception("Object %s is not a reference and cannot be set" % unicode(self))
        
    def dereference( self ):
        # For objects that are references, this operation dereferences them.  All others
        # return self.
        return self

    def dereference_type( self ):
        # For objects that are references, this operation dereferences them.  All others
        # return self.
        return self.type_value( )

# Array object.
class JSArray( JSObject ):

    def __init__ ( self, array_size ):
        """ This javascript array object has a given size and doesn't grow or shrink """
        JSObject.__init__( self )
        self.array_size = array_size
        self.array = { }

    def get_type( self, member_name ):
        if member_name == "length":
            return "number"
        index = int(member_name)
        assert index >=0 and index < self.array_size
        if index >= len(self.array):
            return JSObject.get_type( member_name )
        return self.array[ index ].type_value( )

    def get_value( self, member_name ):
        if member_name == "length":
            return JSNumber( self.array_size )
        index = int(member_name)
        assert index >=0 and index < self.array_size
        if index >= len(self.array):
            return JSNull()
        return self.array[ index ]

    def set_value( self, member_name, value):
        assert isinstance( value, JSObject )
        if member_name == "length":
            raise Exception("Can't set the size of an array after the fact")
        index = int(member_name)
        assert index >=0 and index < self.array_size
        self.array[ index ] = value

    def __str__( self ):
        return "Array of %d items" % self.array_size

    def __unicode__( self ):
        return "Array of %d items" % self.array_size

# Context javascript object.  This is an object that corresponds
# to a scope of a method etc.
class JSScope( JSObject ):

    def __init__ ( self, enclosing_scope ):
        """ This class has the object dictionary """
        JSObject.__init__( self )
        self.members = { }
        self.enclosing_scope = enclosing_scope

    def get_value( self, member_name ):
        """ Look for object, and if it's there, return it.  Otherwise, look in enclosing scope. """
        try:
            return self.members[ member_name ]
        except:
            pass
        if self.enclosing_scope == None:
            return JSObject.get_value( self, member_name )
        return self.enclosing_scope.get_value( self, member_name )

    def define_value( self, member_name, value=None ):
        """ Only return an error if this already exists """
        try:
            test = self.members[ member_name ]
        except:
            self.members[ member_name ] = value
            return
        raise Exception("Duplicate definition for '%s'" % member_name)

    def set_value( self, member_name, value ):
        """ Set the object property, which we find in the same order as for lookup """
        try:
            # Make sure it's already been defined
            test = self.members[ member_name ]
        except:
            if self.enclosing_scope == None:
                return JSObject.set_value( self, member_name, value )
            else:
                return self.enclosing_scope.set_value( self, member_name, value )

        self.members[ member_name ] = value

    def find_symbol( self, symbol_name ):
        """ Get the first parent object for which this symbol exists """
        if symbol_name in self.members:
            test = self.members[ symbol_name ]
            return JSObjMemberReference( self, symbol_name )
        if self.enclosing_scope == None:
            raise Exception("No such variable or method: %s" % symbol_name )
        return self.enclosing_scope.find_symbol( symbol_name )


# Method-type object
class JSMethod( JSObject ):

    def __init__( self, name, arguments, body ):
        JSObject.__init__( self )
        self.name = name
        self.arguments = arguments
        self.body = body

    def call( self, argset, enclosing_scope ):
        # Throw up if argument count doesn't agree
        if len(self.arguments) != len(argset):
            raise Exception("Arguments do not match for method '%s'" % self.name)
        # Set up a new context
        context = JSScope( enclosing_scope )
        # Initialize arguments
        i = 0
        while i < len(self.arguments):
            argument_name = self.arguments[i]
            argument_value = argset[i]
            context.define_value( argument_name )
            context.set_value( argument_name, argument_value )
            i += 1
        # Execute method tokens
        ts = JSTokenStream( self.body )
        response = ts.evaluate_statement( context, "method %s" % self.name )
        if isinstance( response, JSReturnSignal ):
            return response.get_value( )
        else:
            return JSNull( )

# Class method-type object
class JSClassMethod( JSMethod ):

    def __init__( self, name, class_instance, arguments, body ):
        JSMethod.__init__( self, name, ["this"] + arguments, body )
        self.class_instance = class_instance

    def call( self, argset, enclosing_scope ):
        # Tack on class instance to arg set
        return JSMethod.call( self, [ self.class_instance ] + argset, enclosing_scope )

# Class-type object
class JSClass( JSObject ):

    def __init__( self, name, members ):
        JSObject.__init__( self )
        self.name = name
        self.members = members

    def construct( self, argset, enclosing_scope ):
        # Build an instance of the class.  This involves locating a matching constructor in the list of methods,
        # and creating a class instance object from it
        rval = JSClassInstance( self )

        # Initialize member variables
        for (name,value) in self.members:
            rval.set_value(name,value)

        full_args = [ rval ] + argset
        method_member = rval.get_value(build_method_name(self.name,full_args))
        if method_member == None:
            raise Exception("Constructor with %d arguments not found for %s" % (len(argset), self.name))
        # Call the constructor method
        method_member.call( full_args, enclosing_scope )
        # return the object we just constructed
        return rval

# Class instance object
class JSClassInstance( JSObject ):

    def __init__( self, class_definition ):
        JSObject.__init__( self )
        self.class_definition = class_definition
        self.member_variables = { }

    def get_value( self, member_name ):
        return self.member_variables[member_name]

    def set_value( self, member_name, value ):
        self.member_variables[member_name] = value


# Boolean type object
class JSBoolean( JSObject ):

    def __init__( self, value ):
        JSObject.__init__( self )
        self.value = value

    def bool_value( self ):
        return self.value

    def __str__( self ):
        if self.value:
            return "Boolean 'true' value"
        else:
            return "Boolean 'false' value"

    def __unicode__( self ):
        if self.value:
            return "Boolean 'true' value"
        else:
            return "Boolean 'false' value"

# Number-type object
class JSNumber( JSObject ):

    def __init__( self, value ):
        JSObject.__init__( self )
        self.value = value

    def type_value( self ):
        return unicode( "number" )

    def num_value( self ):
        return self.value

    def str_value( self ):
        return unicode( self.value )

    def __str__( self ):
        return "Numeric value ("+unicode(self.value)+")"

    def __unicode__( self ):
        return "Numeric value ("+unicode(self.value)+")"

# Regexp test method
class JSRegexpTestMethod( JSObject ):

    def __init__( self, regexp ):
        JSObject.__init__( self )
        self.regexp = regexp

    def call( self, argset, context ):
        # Need one argument
        if len(argset) != 1:
            raise Exception("Expecting one string argument to test() method")
        testvalue = argset[0].str_value()

        # Do the test.
        # Now, this is a bit dodgy, because I'm using python regular expressions to
        # model javascript regular expressions.  If this gets more than dirt simple, we'll
        # have to interpret the javascript expression and convert it to the py one, but
        # for now we can get away with just using the python stuff for now.
        flags = 0
        if self.regexp.is_regexp_global( ):
            flags += re.MULTILINE
        if self.regexp.is_regexp_insensitive( ):
            flags += re.IGNORECASE
        
        regexp = re.compile( self.regexp.get_regexp( ), flags )

        mo = regexp.match( testvalue )
        rval = (mo != None)

        return JSBoolean( rval )

# Regexp-type object
class JSRegexp( JSObject ):

    def __init__( self, value, is_global, is_insensitive ):
        JSObject.__init__( self )
        self.value = value
        self.is_global = is_global
        self.is_insensitive = is_insensitive

    def type_value( self ):
        return unicode( "regexp" )

    def get_value( self, member_name ):
        # A regexp has a method property for the test method (which is the only one
        # we currently support)
        if member_name == "test":
            return JSRegexpTestMethod( self )
        else:
            return JSObject.get_value( member_name )

    def get_regexp( self ):
        return self.value

    def is_regexp_global( self ):
        return self.is_global

    def is_regexp_insensitive( self ):
        return self.is_insensitive

# Search method
class JSSearch( JSObject ):

    def __init__( self, string_object ):
        JSObject.__init__( self )
        self.string_object = string_object

    def call( self, argset, enclosing_scope ):
        if len(argset) != 1:
            raise Exception("Search requires one string or regexp argument, found %d" % len(argset))
        if argset[0].is_type( JSString ):
            # Do a string search
            value = self.string_object.str_value().find(argset[0].str_value())
            return JSNumber(value)

        elif argset[0].is_type( JSRegexp ):
            # Do a regexp search
            regexp_value = argset[0]
            flags = 0
            if regexp_value.is_regexp_global( ):
                flags += re.MULTILINE
            if regexp_value.is_regexp_insensitive( ):
                flags += re.IGNORECASE

            # If there's an illegal regular expression, we SHOULD throw a Javascript exception, but we don't have exception processing yet...
            # MHL
            regexp = re.compile( regexp_value.get_regexp( ), flags )

            mo = regexp.match( self.string_object.str_value() )
            if mo == None:
                return JSNumber(-1)
            return JSNumber(mo.start())

        else:
            raise Exception("Search requires a regexp or string argument")


# Replace method
class JSReplace( JSObject ):

    def __init__( self, string_object ):
        JSObject.__init__( self )
        self.string_object = string_object

    def call( self, argset, enclosing_scope ):
        if len(argset) != 2:
            raise Exception("Replace requires two arguments: a regexp and a string; found %d" % len(argset))
        if argset[0].is_type( JSRegexp ) == False:
            raise Exception("Replace requires a regexp argument")
        if argset[1].is_type( JSString ) == False:
            raise Exception("Replace requires a string argument")

        regexp_value = argset[0]
        replace_value = argset[1]

        # Scan the current string.  Find every match and replace it with the replace value.
        flags = 0
        if regexp_value.is_regexp_global( ):
            flags += re.MULTILINE
        if regexp_value.is_regexp_insensitive( ):
            flags += re.IGNORECASE

        # If there's an illegal regular expression, we SHOULD throw a Javascript exception, but we don't have exception processing yet...
        # MHL
        regexp = re.compile( regexp_value.get_regexp( ), flags )

        # Here's the output value
        rval = regexp.sub( replace_value.str_value(), self.string_object.str_value() )
        return JSString(rval)

# Index-of method
class JSIndexOf( JSObject ):

    def __init__( self, string_object ):
        JSObject.__init__( self )
        self.string_object = string_object

    def call( self, argset, enclosing_scope ):
        # Throw up if argument count doesn't agree
        if len(argset) != 1:
            raise Exception("IndexOf requires one string argument, found %d" % len(argset))
        if argset[0].is_type( JSString ) == False:
            raise Exception("IndexOf requires a string argument")
        value = self.string_object.str_value().find(argset[0].str_value())
        return JSNumber(value)

# charAt method
class JSCharAt( JSObject ):

    def __init__( self, string_object ):
        JSObject.__init__( self )
        self.string_object = string_object

    def call( self, argset, enclosing_scope ):
        # Throw up if argument count doesn't agree
        if len(argset) != 1:
            raise Exception("CharAt requires one numeric argument, found %d" % len(argset))
        if argset[0].is_type( JSNumber ) == False:
            raise Exception("CharAt requires a numeric argument")
        value = self.string_object.str_value()[int(argset[0].num_value())]
        return JSString(value)

# split method
class JSSplit( JSObject ):
    def __init__( self, string_object ):
        JSObject.__init__( self )
        self.string_object = string_object

    def call( self, argset, enclosing_scope ):
        # Throw up if argument count doesn't agree
        if len(argset) != 1:
            raise Exception("Split requires one string argument, found %d" % len(argset))
        if argset[0].is_type( JSString ) == False:
            raise Exception("Split requires a string argument")
        value = self.string_object.str_value().split(argset[0].str_value())
        # Make an array of strings
        rval = JSArray( len(value) )
        index = 0
        for index in range(0,len(value)):
            rval.set_value(str(index),JSString(value[index]))
        return rval

class JSSubstring( JSObject ):
    def __init__( self, string_object ):
        JSObject.__init__( self )
        self.string_object = string_object

    def call( self, argset, enclosing_scope ):
        string_value = self.string_object.str_value()
        # Throw up if argument count doesn't agree
        if len(argset) < 1 or len(argset) > 2:
            raise Exception("Substring requires one or two numeric arguments, found %d" % len(argset))
        if argset[0].is_type( JSNumber ) == False:
            raise Exception("Substring requires a numeric argument")
        start_value = argset[0].num_value()
        if len(argset) > 1:
            if argset[1].is_type( JSNumber ) == False:
                raise Exception("Substring requires a second numeric argument")
            end_value = argset[1].num_value()
        else:
            end_value = len(string_value)
        return JSString(string_value[start_value:end_value])

# String-type object
class JSString( JSObject ):

    def __init__( self, value ):
        JSObject.__init__( self )
        assert isinstance( value, str ) or isinstance( value, unicode )
        self.value = value

    def get_value( self, member_name ):
        if member_name == "indexOf":
            return JSIndexOf( self )
        if member_name == "charAt":
            return JSCharAt( self )
        if member_name == "length":
            return JSNumber( len(self.value) )
        if member_name == "search":
            return JSSearch( self )
        if member_name == "replace":
            return JSReplace( self )
        if member_name == "split":
            return JSSplit( self )
        if member_name == "substring":
            return JSSubstring( self )
        return JSObject.get_value( self, member_name )

    def type_value( self ):
        return unicode( "string" )

    def str_value( self ):
        return unicode( self.value )

    def num_value( self ):
        try:
            return float(self.value)
        except:
            return JSObject.num_value( self )

    def __str__( self ):
        return "String value ("+unicode(self.value)+")"

    def __unicode__( self ):
        return "String value ("+unicode(self.value)+")"

# Null
class JSNull( JSObject ):

    def __init__( self ):
        JSObject.__init__( self )

    def __str__( self ):
        return "Null value"

    def __unicode__( self ):
        return "Null value"

    def bool_value( self ):
        return False

# Javascript member reference
class JSReference( JSObject ):
    def __init__( self ):
        JSObject.__init__( self )

    def is_type( self, type ):
        return self.dereference().is_type(type)

    def call( self, argset, context ):
        return self.dereference().call(argset,context)

    def get_type( self, member_name ):
        return self.dereference().get_type(member_name)

    def get_value( self, member_name ):
        return self.dereference().get_value(member_name)

    def set_value( self, member_name, value ):
        self.dereference().set_value(member_name,value)

    def type_value( self ):
        return self.dereference_type()

    def str_value( self ):
        return self.dereference().str_value()

    def num_value( self ):
        return self.dereference().num_value()

    def bool_value( self ):
        return self.dereference().bool_value()

    def construct( self, argset, context ):
        return self.dereference().construct(argset,context)

# object/member style reference
class JSObjMemberReference( JSReference ):
    def __init__( self, obj, member ):
        JSReference.__init__( self )
        assert isinstance( member, str ) or isinstance( member, unicode )
        assert isinstance( obj, JSObject )
        self.object = obj
        self.member = member

    def dereference( self ):
        return self.object.get_value( self.member )

    def dereference_type( self ):
        return self.object.get_type( self.member )

    def set_reference( self, newobject ):
        self.object.set_value( self.member, newobject )


# Javascript statement return signal
class JSSignal:
    def __init__( self ):
        pass

class JSBreakSignal( JSSignal ):
    def __init__( self ):
        JSSignal.__init__( self )

class JSContinueSignal( JSSignal ):
    def __init__( self ):
        JSSignal.__init__( self )

class JSReturnSignal( JSSignal ):
    def __init__( self, value ):
        JSSignal.__init__( self )
        self.value = value

    def get_value( self ):
        return self.value

# Javascript token.
class JSToken:
    def __init__( self, punc_value=None, string_value=None, int_value=None, float_value=None, symbol_value=None, regexp_value=None, regexp_global=False, regexp_insensitive=False ):
        self.punc_value = punc_value
        self.string_value = string_value
        self.int_value = int_value
        self.float_value = float_value
        self.symbol_value = symbol_value
        self.regexp_value = regexp_value
        self.regexp_global = regexp_global
        self.regexp_insensitive = regexp_insensitive

    def __str__( self ):
        if self.punc_value != None:
            return "Punctuation: "+str(self.punc_value)
        elif self.string_value != None:
            return "String: '"+str(self.string_value)+"'"
        elif self.int_value != None:
            return "Int: "+str(self.int_value)
        elif self.float_value != None:
            return "Float: "+str(self.float_value)
        elif self.regexp_value != None:
            rexp = "Regexp: "+str(self.regexp_value)+"("
            if self.regexp_global:
                rexp += "g"
            if self.regexp_insensitive:
                rexp += "i"
            return rexp + ")"
        else:
            return "Symbol: "+str(self.symbol_value)

    def __unicode__( self ):
        if self.punc_value != None:
            return "Punctuation: "+unicode(self.punc_value)
        elif self.string_value != None:
            return "String: '"+unicode(self.string_value)+"'"
        elif self.int_value != None:
            return "Int: "+unicode(self.int_value)
        elif self.float_value != None:
            return "Float: "+unicode(self.float_value)
        elif self.regexp_value != None:
            rexp = "Regexp: "+unicode(self.regexp_value)+"("
            if self.regexp_global:
                rexp += "g"
            if self.regexp_insensitive:
                rexp += "i"
            return rexp + ")"
        else:
            return "Symbol: "+unicode(self.symbol_value)

    def get_punc( self ):
        return self.punc_value

    def get_string( self ):
        return self.string_value

    def get_int( self ):
        return self.int_value

    def get_float( self ):
        return self.float_value

    def get_symbol( self ):
        return self.symbol_value

    def get_regexp( self ):
        return self.regexp_value

    def get_regexp_global( self ):
        return self.regexp_global

    def get_regexp_insensitive( self ):
        return self.regexp_insensitive

# Javascript token stream
class JSTokenStream:
    def __init__( self, body ):
        self.body = body
        self.start_index = 0
        self.current_position = 0
        self.current_token = None

    def advance( self ):
        self.current_token = None
        self.current_position = self.start_index

    def peek( self, slash_is_legal=False ):
        if self.current_token == None:
            self.current_position = self.start_index
            self.current_token = self.build_next( slash_is_legal )
        return self.current_token

    def get_position( self ):
        return self.current_position

    def get_chunk( self, start_position ):
        return self.body[ start_position : self.current_position ]

    def set_position( self, position ):
        self.start_index = position
        self.current_position = position
        self.current_token = None

    # Evaluate a set of statements (e.g. definitions)
    def evaluate_statement_list( self, context, place="global defs" ):
        while True:
            token = self.peek( )
            if token == None:
                break
            self.evaluate_statement( context, place )

    # Evaluate statement
    # Returns a return status object (of type PSSignal), which signals
    # what to do: break loop, continue loop, return (with value or not), or next statement
    def evaluate_statement( self, context, place ):
        # statement -> "{" statement1 ... statementn "}"
        # statement -> expr ";"
        # statement -> "var" symbol ["=" expr] ";"
        # statement -> "function" string "(" [string ["," string ...]] ")" statement
        # statement -> "if" "(" expr ")" "then" statement ["else" statement]
        # statement -> "while" "(" expr ")" statement
        # statement -> "break" ";"
        # statement -> "continue" ";"
        # statement -> "return" expr ";"
        # statement -> "try" statement catchlist

        token = self.peek( )
        if token == None:
            raise Exception("Unexpected end of code when looking for a statement, in %s" % place)
        if token.get_punc( ) == "{":
            # compound statement; evaluate them all in sequence, but also in a new scope
            newscope = JSScope( context )
            self.advance( )
            execute_enabled = True
            while True:
                token = self.peek( )
                if token == None:
                    raise Exception("Unexpected end of statement block in %s" % place)
                if token.get_punc( ) == "}":
                    break
                if execute_enabled:
                    result = self.evaluate_statement( newscope, place )
                    if isinstance(result, JSContinueSignal) or isinstance(result, JSReturnSignal) or isinstance(result, JSBreakSignal):
                        execute_enabled = False
                else:
                    self.skip_statement( )
            self.advance( )
            if execute_enabled == False:
                return result
            return None

        elif token.get_symbol( ) == "var":
            self.advance( )
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of var statement")
            if token.get_symbol( ) == None:
                raise Exception("Var statement: Expecting a symbol, saw %s in %s" % (unicode(token), place))
            self.advance( )
            varname = token.get_symbol( )
            # Look for '='
            token = self.peek( )
            if token != None and token.get_punc( ) == "=":
                # It has a value.
                self.advance( )
                value = self.evaluate_expr( context, place )
                if value == None:
                    raise Exception("Expected expression after =, in %s" % place)
            else:
                value = None
            # Define symbol
            context.define_value( varname, value )
            token = self.peek( )
            if token == None:
                raise Exception("Didn't find expected ';' at end of var statement defining '%s', saw EOF, in %s" % (varname, place))
            if token.get_punc( ) == ";":
                self.advance( )
                return None
            else:
                unknown_tokens = [ unicode(token) ]
                while True:
                    self.advance( )
                    token = self.peek( )
                    if token == None or token.get_punc( ) == ";":
                        raise Exception("Didn't find expected ';' at end of var statement defining '%s', saw '%s', in %s" % (varname, unicode(unknown_tokens),place))
                    unknown_tokens += [ unicode(token) ]

        elif token.get_symbol( ) == "if":
            self.advance( )
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of conditional in %s" % place)
            if token.get_punc( ) != "(":
                raise Exception("Expecting '(' in if")
            self.advance( )
            # Evaluate the expression
            condition_result = self.evaluate_expr( context, place )
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of conditional in %s" % place)
            if token.get_punc( ) != ")":
                raise Exception("Expecting ')' in if")
            self.advance( )

            if condition_result.bool_value( ):
                rval = self.evaluate_statement( context, place )
                token = self.peek( )
                if token != None and token.get_symbol( ) == "else":
                    self.advance( )
                    self.skip_statement( )
            else:
                rval = None
                self.skip_statement( )
                token = self.peek( )
                if token != None and token.get_symbol( ) == "else":
                    self.advance( )
                    rval = self.evaluate_statement( context, place )
            return rval

        elif token.get_symbol( ) == "while":
            self.advance( )
            # For while's, we need to remember the start of the expression, as well as the
            # start of the "statement"
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of while in %s" % place)
            if token.get_punc( ) != "(":
                raise Exception("Expecting '(' in while, saw %s, in %s" % (unicode(token), place))
            self.advance( )
            expression_begin = self.get_position( )
            while True:
                continue_value = self.evaluate_expr( context, place )
                token = self.peek( )
                if token == None:
                    raise Exception("Unexpected end of while in %s" % place)
                if token.get_punc( ) != ")":
                    raise Exception("Expecting ')' in while, saw %s, in %s" % (unicode(token), place))
                self.advance( )
                if continue_value.bool_value( ):
                    result = self.evaluate_statement( context, place )
                    if isinstance(result, JSReturnSignal):
                        return result
                    if isinstance(result, JSBreakSignal):
                        # already at end of statement
                        return None
                    # Continue just goes around again
                else:
                    self.skip_statement( )
                    return None
                self.set_position( expression_begin )
                # Repeat!

        elif token.get_symbol( ) == "break":
            self.advance( )
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of break in %s" % place)
            if token.get_punc( ) != ";":
                raise Exception("Expecting ';' in break, saw %s, in %s" % (unicode(token), place))
            self.advance( )
            return JSBreakSignal( )

        elif token.get_symbol( ) == "continue":
            self.advance( )
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of continue in %s" % place)
            if token.get_punc( ) != ";":
                raise Exception("Expecting ';' in continue, saw %s, in %s" % (unicode(token), place))
            self.advance( )
            return JSContinueSignal( )

        elif token.get_symbol( ) == "return":
            self.advance( )
            rval = self.evaluate_expr( context, place )
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of return in %s" % place)
            if token.get_punc( ) != ";":
                raise Exception("Expecting ';' in return, saw %s, in %s" % (unicode(token), place))
            self.advance( )
            return JSReturnSignal( rval )

        elif token.get_symbol( ) == "try":
            self.advance( )
            # Parse and execute the statement.  Note that this code does NOT yet actually do anything that could throw or catch exceptions!
            # It just pretends the exception is never thrown, at the moment.
            result = self.evaluate_statement( context, place )
            saw_something = False
            # Now, look for the catch clause
            token = self.peek( )
            if token != None and token.get_symbol() == "catch":
                self.advance( )
                saw_something = True
                # Process the catch, and advance.
                token = self.peek( )
                if token == None:
                    raise Exception("Missing ( after catch, found EOF, in %s" % place)
                if token.get_punc( ) != "(":
                    raise Exception("Missing (, found %s, in %s" % (unicode(token),place) )
                self.advance( )
                # Grab the name of a symbol
                token = self.peek( )
                if token == None:
                    raise Exception("Missing symbol after catch(, found EOF, in %s" % place)
                if token.get_symbol() == None:
                    raise Exception("Missing symbol after catch(, found %s, in %s" % (unicode(token),place) )
                catch_symbol = token.get_symbol( )
                self.advance()
                token = self.peek( )
                if token == None:
                    raise Exception("Missing ) after catch, found EOF, in %s" % place)
                if token.get_punc() != ")":
                    raise Exception("Missing ) after catch, found %s, in %s" % (unicode(token),place))
                self.advance()
                # Skip the statement, since we never do any exception catching at this time.
                self.skip_statement( )
                token = self.peek( )
            if token != None and token.get_symbol() == "finally":
                self.advance( )
                saw_something = True
                result2 = self.evaluate_statement( context, place )
                if result2 != None:
                    result = result2

            if saw_something == False:
                if token == None:
                    raise Exception("Missing catch or finally, in %s; instead saw EOF" % place)
                raise Exception("Missing catch or finally, in %s; instead saw %s" % (place,unicode(token)))

            return result

        elif token.get_symbol( ) == "function":
            self.advance( )
            token = self.peek( )
            if token == None:
                raise Exception("Missing function name, found EOF, in %s" % place)
            function_name = token.get_symbol( )
            if function_name == None:
                raise Exception("Missing function name, found %s, in %s" % (unicode(token), place))
            self.advance( )
            token = self.peek( )
            if token == None:
                raise Exception("Missing '(', found EOF, in %s" % place)
            if token.get_punc( ) != "(":
                raise Exception("Missing '(', found %s, in %s" % (unicode(token), place))
            self.advance( )
            argnames = [ ]
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of function definition, found EOF, in %s" % place)
            if token.get_punc( ) != ")":
                while True:
                    token = self.peek( )
                    if token == None:
                        raise Exception("Unexpected end of function definition, found EOF, in %s" % place)
                    arg = token.get_symbol( )
                    if arg == None:
                        raise Exception("Expecting an argument name, found %s, in %s" % (unicode(token),place))
                    argnames.append( arg )
                    self.advance( )
                    token = self.peek( )
                    if token == None:
                        raise Exception("Unexpected end of function definition, found EOF, in %s" % place)
                    if token.get_punc( ) == ")":
                        break
                    if token.get_punc( ) == ",":
                        self.advance( )
                    else:
                        raise Exception("Expected ',', found %s, in %s" % (unicode(token), place))
            self.advance( )

            # Record start of body
            begin_position = self.get_position( )
            self.skip_statement( )
            func_body = self.get_chunk( begin_position )
            function_object = JSMethod( function_name, argnames, func_body )
            context.define_value( function_name )
            context.set_value( function_name, function_object )
            return None

        else:
            # Process expression
            if self.evaluate_expr( context, place ) == None:
                token = self.peek( )
                if token == None:
                    raise Exception("Expected a statement, saw EOF, in %s" % place)
                else:
                    raise Exception("Expected a statement, saw %s, in %s" % (unicode(token),place) )
            token = self.peek( )
            if token == None:
                raise Exception("Unexpected end of expression, found EOL, in %s" % place)
            if token.get_punc( ) != ";":
                raise Exception("Expecting ';' after expression, found %s, in %s" % (unicode(token), place))
            self.advance( )
            return None


    def skip_statement( self ):
        # Skip statement to end; used in recording method definition
        # This understands statement syntax but does not attempt to parse expressions.
        token = self.peek( )
        if token == None:
            raise Exception("Unexpected end of statement")
        if token.get_punc( ) == "{":
            self.advance( )
            while True:
                token = self.peek( )
                if token == None:
                    raise Exception("Unexpected end of statement block")
                if token.get_punc( ) == "}":
                    break
                self.skip_statement( )
            self.advance( )
        elif token.get_symbol( ) == "while":
            self.advance( )
            self.skip_conditional( )
            self.skip_statement( )
        elif token.get_symbol( ) == "if":
            self.advance( )
            self.skip_conditional( )
            self.skip_statement( )
            token = self.peek( )
            if token != None and token.get_symbol( ) == "else":
                self.advance( )
                self.skip_statement( )
        elif token.get_symbol( ) == "for":
            self.advance( )
            self.skip_conditional( )
            self.skip_statement( )
        elif token.get_symbol( ) == "try":
            self.advance( )
            self.skip_statement( )
            # Now, look for the catch clause
            token = self.peek( )
            if token != None and token.get_symbol() == "catch":
                self.advance( )
                self.skip_conditional( )
                self.skip_statement( )
                token = self.peek( )
            if token != None and token.get_symbol() == "finally":
                self.advance( )
                self.skip_statement( )
        else:
            # Process up to terminating semicolon token
            unknown_tokens = [ ]
            while True:
                token = self.peek( )
                if token == None:
                    raise Exception("Unexpected end of statement; unknown tokens: '%s'; need semicolon" % str(unknown_tokens))
                unknown_tokens += [ str(token) ]
                self.advance( )
                if token.get_punc( ) == ";":
                    break

    def skip_conditional( self ):
        # Walk through nested parens
        token = self.peek( )
        if token == None or token.get_punc( ) != "(":
            raise Exception("Missing '(' in while/conditional")
        self.advance( )
        count = 1
        while True:
            token = self.peek( )
            self.advance( )
            if token == None:
                raise Exception("Unexpected end of while statement conditional")

            if token.get_punc( ) == "(":
                count += 1
            elif token.get_punc( ) == ")":
                count -= 1
                if count == 0:
                    break

    # Evaluate expression.  This is approximately correct, good enough for all
    # our current needs.  If we develop heavy javascript evaluation requirements this
    # expression evaluation should be refined to be more rigorously correct.
    # Returns a return object, or none for "not me"
    def evaluate_expr( self, context, place, parse_only=False ):
        # expr = expr [...]
        # expr += expr [...]
        # expr -= expr [...]
        # expr *= expr [...]
        # expr /= expr [...]
        rval = self.evaluate_expr0( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "=":
                # = operator
                self.advance( )
                nextvalue = self.evaluate_expr0( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '=' in %s" % place)
                if parse_only==False:
                    rval = self.assign(rval, nextvalue)
            elif token != None and token.get_punc( ) == "+=":
                # += operator
                self.advance( )
                nextvalue = self.evaluate_expr0( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '+=' in %s" % place)
                if parse_only==False:
                    rval = self.assign_plus(rval, nextvalue)
            elif token != None and token.get_punc( ) == "-=":
                # += operator
                self.advance( )
                nextvalue = self.evaluate_expr0( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '-=' in %s" % place)
                if parse_only==False:
                    rval = self.assign_minus(rval, nextvalue)
            elif token != None and token.get_punc( ) == "*=":
                # += operator
                self.advance( )
                nextvalue = self.evaluate_expr0( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '*=' in %s" % place)
                if parse_only==False:
                    rval = self.assign_plus(rval, nextvalue)
            elif token != None and token.get_punc( ) == "/=":
                # += operator
                self.advance( )
                nextvalue = self.evaluate_expr0( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '/=' in %s" % place)
                if parse_only==False:
                    rval = self.assign_minus(rval, nextvalue)
            else:
                return rval

        return self.evaluate_expr0( context, place, parse_only )
        
    def assign( self, value1, value2 ):
        value1.set_reference( value2.dereference( ) )
        return value1

    def assign_plus( self, value1, value2 ):
        rval = self.plus( value1, value2 )
        value1.set_reference( rval.dereference( ) )
        return value1
    
    def assign_minus( self, value1, value2 ):
        rval = self.minus( value1, value2 )
        value1.set_reference( rval.dereference( ) )
        return value1

    def assign_times( self, value1, value2 ):
        rval = self.times( value1, value2 )
        value1.set_reference( rval.dereference( ) )
        return value1
    
    def assign_divide( self, value1, value2 ):
        rval = self.divide( value1, value2 )
        value1.set_reference( rval.dereference( ) )
        return value1
        
    def evaluate_expr0( self, context, place, parse_only=False ):
        # expr0 -> expr1 ["||" expr1 [...]]
        rval = self.evaluate_expr1( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "||":
                # || operator
                self.advance( )
                if parse_only==False and rval.bool_value( ):
                    parse_only = True
                nextvalue = self.evaluate_expr1( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '||' in %s" % place)
                if parse_only==False:
                    rval = self.logical_or(rval, nextvalue)
            else:
                return rval

    def logical_or( self, value1, value2 ):
        return JSBoolean( value1.bool_value( ) or value2.bool_value( ) )

    def evaluate_expr1( self, context, place, parse_only=False ):
        # expr1 -> expr2 ["&&" expr2 [...]]
        rval = self.evaluate_expr2( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "&&":
                # && operator
                self.advance( )
                if parse_only==False and rval.bool_value( )==False:
                    parse_only = True
                nextvalue = self.evaluate_expr2( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '&&' in %s" % place)
                if parse_only==False:
                    rval = self.logical_and(rval, nextvalue)
            else:
                return rval

    def logical_and( self, value1, value2 ):
        return JSBoolean( value1.bool_value( ) and value2.bool_value( ) )

    def evaluate_expr2( self, context, place, parse_only=False ):
        # expr2 -> expr3 ["|" expr3 [...]]
        rval = self.evaluate_expr3( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "|":
                # | operator
                self.advance( )
                nextvalue = self.evaluate_expr3( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '|' in %s" % place)
                if parse_only==False:
                    rval = self.numerical_or(rval, nextvalue)
            else:
                return rval

    def numerical_or( self, value1, value2 ):
        return JSNumber( int(value1.num_value( ) ) | int(value2.num_value( ) ) )

    def evaluate_expr3( self, context, place, parse_only=False ):
        # expr3 -> expr4 ["^" expr4 [...]]
        rval = self.evaluate_expr4( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "^":
                # | operator
                self.advance( )
                nextvalue = self.evaluate_expr4( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '^' in %s" % place)
                if parse_only==False:
                    rval = self.numerical_xor(rval, nextvalue)
            else:
                return rval

    def numerical_xor( self, value1, value2 ):
        return JSNumber( int(value1.num_value( ) ) ^ int(value2.num_value( ) ) )

    def evaluate_expr4( self, context, place, parse_only=False ):
        # expr4 -> expr5 ["&" expr5 [...]]
        rval = self.evaluate_expr5( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "&":
                # & operator
                self.advance( )
                nextvalue = self.evaluate_expr5( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '&' in %s" % place)
                if parse_only==False:
                    rval = self.numerical_and(rval, nextvalue)
            else:
                return rval

    def numerical_and( self, value1, value2 ):
        return JSNumber( int(value1.num_value( ) ) & int(value2.num_value( ) ) )

    def evaluate_expr5( self, context, place, parse_only=False ):
        # expr5 -> expr6 ["==" expr6 [...]]
        # expr5 -> expr6 ["!=" expr6 [...]]
        rval = self.evaluate_expr6( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "==":
                # == operator
                self.advance( )
                nextvalue = self.evaluate_expr6( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '==' in %s" % place)
                if parse_only==False:
                    rval = self.equals(rval, nextvalue)
            elif token != None and token.get_punc( ) == "!=":
                # != operator
                self.advance( )
                nextvalue = self.evaluate_expr6( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '!=' in %s" % place)
                if parse_only==False:
                    rval = self.not_equals(rval, nextvalue)
            else:
                return rval

    def equals( self, value1, value2 ):
        # If these are both strings, compare as strings
        if value1.is_type(JSString) and value2.is_type(JSString):
            return JSBoolean( value1.str_value( ) == value2.str_value( ) )
        elif value1.is_type(JSBoolean) and value2.is_type(JSBoolean):
            return JSBoolean( value1.bool_value( ) == value2.bool_value( ) )
        return JSBoolean( value1.num_value( ) == value2.num_value( ) )

    def not_equals( self, value1, value2 ):
        # If these are both strings, compare as strings
        if value1.is_type(JSString) and value2.is_type(JSString):
            return JSBoolean( value1.str_value( ) != value2.str_value( ) )
        elif value1.is_type(JSBoolean) and value2.is_type(JSBoolean):
            return JSBoolean( value1.bool_value( ) != value2.bool_value( ) )
        return JSBoolean( value1.num_value( ) != value2.num_value( ) )

    def evaluate_expr6( self, context, place, parse_only=False ):
        # expr6 -> expr7 [">" expr7 [...]]
        # expr6 -> expr7 ["<" expr7 [...]]
        # expr6 -> expr7 [">=" expr7 [...]]
        # expr6 -> expr7 ["<=" expr7 [...]]
        rval = self.evaluate_expr7( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "<=":
                # <= operator
                self.advance( )
                nextvalue = self.evaluate_expr7( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '<=' in %s" % place)
                if parse_only==False:
                    rval = self.less_than_or_equals(rval, nextvalue)
            elif token != None and token.get_punc( ) == ">=":
                # >= operator
                self.advance( )
                nextvalue = self.evaluate_expr7( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '>=' in %s" % place)
                if parse_only==False:
                    rval = self.greater_than_or_equals(rval, nextvalue)
            elif token != None and token.get_punc( ) == "<":
                # < operator
                self.advance( )
                nextvalue = self.evaluate_expr7( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '<' in %s" % place)
                if parse_only==False:
                    rval = self.less_than(rval, nextvalue)
            elif token != None and token.get_punc( ) == ">":
                # > operator
                self.advance( )
                nextvalue = self.evaluate_expr7( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '>' in %s" % place)
                if parse_only==False:
                    rval = self.greater_than(rval, nextvalue)

            else:
                return rval

    def less_than_or_equals( self, value1, value2 ):
        return JSBoolean( value1.num_value( ) <= value2.num_value( ) )

    def greater_than_or_equals( self, value1, value2 ):
        return JSBoolean( value1.num_value( ) >= value2.num_value( ) )

    def less_than( self, value1, value2 ):
        return JSBoolean( value1.num_value( ) < value2.num_value( ) )

    def greater_than( self, value1, value2 ):
        return JSBoolean( value1.num_value( ) > value2.num_value( ) )

    def evaluate_expr7( self, context, place, parse_only=False ):
        # >>, << TBD
        return self.evaluate_expr8( context, place, parse_only )

    def evaluate_expr7( self, context, place, parse_only=False ):
        # expr7 -> expr8 ["+" expr8 [...]]
        # expr7 -> expr8 ["-" expr8 [...]]
        rval = self.evaluate_expr8( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "+":
                # + operator
                self.advance( )
                nextvalue = self.evaluate_expr8( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '+' in %s" % place)
                if parse_only==False:
                    rval = self.plus(rval, nextvalue)
            elif token != None and token.get_punc( ) == "-":
                # - operator
                self.advance( )
                nextvalue = self.evaluate_expr8( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '-' in %s" % place)
                if parse_only==False:
                    rval = self.minus(rval, nextvalue)
            else:
                return rval

    def plus( self, value1, value2 ):
        if value1.is_type(JSString) or value2.is_type(JSString):
            return JSString( value1.str_value( ) + value2.str_value( ) )
        return JSNumber( value1.num_value( ) + value2.num_value( ) )

    def minus( self, value1, value2 ):
        return JSNumber( value1.num_value( ) - value2.num_value( ) )

    def evaluate_expr8( self, context, place, parse_only=False ):
        # expr8 -> expr9 ["*" expr9 [...]]
        # expr8 -> expr9 ["/" expr9 [...]]
        # expr8 -> expr9 ["%" expr9 [...]]
        rval = self.evaluate_expr9( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( slash_is_legal=True )
            if token != None and token.get_punc( ) == "*":
                # * operator
                self.advance( )
                nextvalue = self.evaluate_expr9( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '*' in %s" % place)
                if parse_only==False:
                    rval = self.times(rval, nextvalue)
            elif token != None and token.get_punc( ) == "/":
                # / operator
                self.advance( )
                nextvalue = self.evaluate_expr9( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '/' in %s" % place)
                if parse_only==False:
                    rval = self.divide(rval, nextvalue)
            elif token != None and token.get_punc( ) == "%":
                # % operator
                self.advance( )
                nextvalue = self.evaluate_expr9( context, place, parse_only )
                if nextvalue == None:
                    raise Exception("Missing expression after '%' in %s" % place)
                if parse_only==False:
                    rval = self.modulo(rval, nextvalue)
            else:
                return rval

    def times( self, value1, value2 ):
        return JSNumber( value1.num_value( ) * value2.num_value( ) )

    def divide( self, value1, value2 ):
        return JSNumber( value1.num_value( ) / value2.num_value( ) )

    def modulo( self, value1, value2 ):
        return JSNumber( value1.num_value( ) % value2.num_value( ) )

    def evaluate_expr9( self, context, place, parse_only=False ):
        # expr9 -> "!" expr9
        # expr9 -> "-" expr9
        # expr9 -> "++" expr9
        # expr9 -> "--" expr9
        # expr9 -> expr10 ["++" ...]
        # expr9 -> expr10 ["--" ...]
        token = self.peek( )
        if token != None and token.get_punc( ) == "!":
            self.advance( )
            nextvalue = self.evaluate_expr9( context, place, parse_only )
            if nextvalue == None:
                raise Exception("Missing expression after '!' in place %s" % place    )
            if parse_only:
                return JSNull()
            return self.logical_not( nextvalue )
        elif token != None and token.get_punc( ) == "-":
            self.advance( )
            nextvalue = self.evaluate_expr9( context, place, parse_only )
            if nextvalue == None:
                raise Exception("Missing expression after '-' in %s" % place)
            if parse_only:
                return JSNull()
            return self.negate ( nextvalue )
        elif token != None and token.get_punc( ) == "--":
            self.advance( )
            nextvalue = self.evaluate_expr9( context, place, parse_only )
            if nextvalue == None:
                raise Exception("Missing expression after '--' in %s" % place)
            if parse_only:
                return JSNull()
            return self.pre_minusminus ( nextvalue )
        elif token != None and token.get_punc( ) == "+":
            self.advance( )
            nextvalue = self.evaluate_expr9( context, place, parse_only )
            if nextvalue == None:
                raise Exception("Missing expression after '+' in %s" % place)
            if parse_only:
                return JSNull()
            return self.positive ( nextvalue )
        elif token != None and token.get_punc( ) == "++":
            self.advance( )
            nextvalue = self.evaluate_expr9( context, place, parse_only )
            if nextvalue == None:
                raise Exception("Missing expression after '++' in %s" % place)
            if parse_only:
                return JSNull()
            return self.pre_plusplus ( nextvalue )
        elif token != None and token.get_symbol( ) == "typeof":
            # typeof operator
            self.advance( )
            nextvalue = self.evaluate_expr9( context, place, parse_only )
            if nextvalue == None:
                raise Exception("Missing expression after 'typeof' in %s" % place)
            if parse_only:
                return JSNull()
            return self.typeof( nextvalue ) 
        elif token != None and token.get_symbol( ) == "new":
            self.advance( )
            token = self.peek( )
            if token == None or token.get_symbol( ) == None:
                raise Exception("new expression missing class name in %s" % place)
            object_name = token.get_symbol( )
            self.advance( )
            token = self.peek( )
            if token == None or token.get_punc( ) != "(":
                raise Exception("constructor argument clause missing")
            self.advance( )
            arguments = [ ]
            token = self.peek( )
            if token == None:
                raise Exception("Missing expression in constructor argument list, saw EOF in %s" % place)
            if token.get_punc( ) == ")":
                self.advance( )
            else:
                while True:
                    nextvalue = self.evaluate_expr( context, place, parse_only )
                    if nextvalue == None:
                        raise Exception("Missing expression in constructor actual arguments in %s" % place)
                    arguments.append( nextvalue )
                    token = self.peek( )
                    if token == None:
                        raise Exception("Missing ',' in constructor actual argument list, saw EOF")
                    if token.get_punc( ) == ")":
                        self.advance( )
                        break
                    if token.get_punc( ) != ",":
                        raise Exception("Missing ',' in constructor actual argument list, saw %s instead, in %s" % (unicode(token),place))
                    self.advance( )
            # Invoke method
            if parse_only:
                return JSObject()
            value = context.find_symbol( object_name )
            if value == None:
                raise Exception("Could not locate symbol %s in %s for construction" % (token.get_symbol(),place))

            return value.construct( arguments, context )

        rval = self.evaluate_expr10( context, place, parse_only )
        if rval == None:
            return rval
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == "++":
                # ++ operator
                self.advance( )
                if parse_only==False:
                    rval = self.post_plusplus( rval )
            elif token != None and token.get_punc( ) == "--":
                # -- operator
                self.advance( )
                if parse_only==False:
                    rval = self.post_minusminus( rval )
            else:
                return rval
        
    def logical_not( self, value1 ):
        return JSBoolean( not value1.bool_value( ) )

    def negate( self, value1 ):
        return JSNumber( -value1.num_value( ) )

    def positive( self, value1 ):
        return JSNumber( +value1.num_value( ) )

    def typeof( self, value1 ):
        return JSString( value1.type_value( ) )

    def post_minusminus( self, value1 ):
        rval = value1.dereference( )
        value1.set_reference( self.minus( value1, JSNumber( 1 ) ).dereference( ) )
        return rval
      
    def post_plusplus( self, value1 ):
        rval = value1.dereference( )
        value1.set_reference( self.plus( value1, JSNumber( 1 ) ).dereference( ) )
        return rval

    def pre_minusminus( self, value1 ):
        value1.set_reference( self.minus( value1, JSNumber( 1 ) ).dereference( ) )
        return value1
      
    def pre_plusplus( self, value1 ):
        value1.set_reference( self.plus( value1, JSNumber( 1 ) ).dereference( ) )
        return value1

    def evaluate_expr10( self, context, place, parse_only=False ):
        # expr10 -> expr11
        # expr10 -> expr11 "(" [expr ["," ...]] ")"
        # expr10 -> expr11 "[" [expr "]"
        # expr10 -> expr11 "." member_name
        
        # Look for a terminal token
        reference_object = self.evaluate_expr11( context, place, parse_only )
        if reference_object == None:
            return None

        # Terminal token object found!
        # Parse trailing operations, if any
        while True:
            token = self.peek( )
            if token != None and token.get_punc( ) == ".":
                self.advance( )
                token = self.peek( )
                if token == None or token.get_symbol( ) == None:
                    raise Exception("Expecting member name in %s" % place)
                self.advance( )
                if parse_only==False:
                    reference_object = JSObjMemberReference( reference_object, token.get_symbol( ) )
            elif token != None and token.get_punc( ) == "[":
                self.advance( )
                value = self.evaluate_expr( context, place, parse_only )
                if value == None:
                    raise Exception("Expecting index value in %s" % place)
                token = self.peek( )
                if token == None or token.get_punc( ) != "]":
                    raise Exception("Expecting ']' in %s" % place)
                self.advance( )
                if parse_only==False:
                    reference_object = JSObjMemberReference( reference_object, str(int(value.num_value( ))) )
            elif token != None and token.get_punc( ) == "(":
                # Method invocation
                self.advance( )
                arguments = [ ]
                token = self.peek( )
                if token == None:
                    raise Exception("Missing expression in argument list, saw EOF in %s" % place)
                if token.get_punc( ) == ")":
                    self.advance( )
                else:
                    while True:
                        nextvalue = self.evaluate_expr( context, place, parse_only )
                        if nextvalue == None:
                            raise Exception("Missing expression in actual arguments in %s" % place)
                        arguments.append( nextvalue )
                        token = self.peek( )
                        if token == None:
                            raise Exception("Missing ',' in actual argument list, saw EOF")
                        if token.get_punc( ) == ")":
                            self.advance( )
                            break
                        if token.get_punc( ) != ",":
                            raise Exception("Missing ',' in actual argument list, saw %s instead, in %s" % (unicode(token),place))
                        self.advance( )
                # Invoke method
                if parse_only:
                    reference_object = JSNull()
                else:
                    reference_object = reference_object.call( arguments, context )

            else:
                break

        return reference_object


    def evaluate_expr11( self, context, place, parse_only=False ):
        # Parse a terminal value, and return the corresponding object.  A "None" return
        # means "not me".

        token = self.peek( )

        if token != None and token.get_symbol( ) == "true":
            self.advance( )
            return JSBoolean( True )
        if token != None and token.get_symbol( ) == "false":
            self.advance( )
            return JSBoolean( False )
        if token != None and token.get_symbol( ) == "null":
            self.advance( )
            return JSNull( )
        if token != None and token.get_string( ) != None:
            self.advance( )
            return JSString( token.get_string( ) )
        if token != None and token.get_int( ) != None:
            self.advance( )
            return JSNumber( token.get_int( ) )
        if token != None and token.get_float( ) != None:
            self.advance( )
            return JSNumber( token.get_float( ) )
        if token != None and token.get_regexp( ) != None:
            self.advance( )
            return JSRegexp( token.get_regexp( ), token.get_regexp_global( ), token.get_regexp_insensitive( ) )

        if token != None and token.get_symbol( ) != None:
            self.advance( )
            if parse_only == False:
                value = context.find_symbol( token.get_symbol( ) )
                if value == None:
                    raise Exception("Could not locate symbol %s in %s" % (token.get_symbol(),place))
                return value
            else:
                return JSObject( )

        if token != None and token.get_punc( ) == "(":
            self.advance( )
            rval = self.evaluate_expr( context, place, parse_only )
            token = self.peek( )
            if token == None or token.get_punc( ) != ")":
                raise Exception("Missing right parenthesis in %s" % place)
            self.advance( )
            return rval

        return None

    # Build a token, starting at the current pointer, and return it.
    # Return None if end.  Advance the current pointer appropriately.
    def build_next( self, slash_is_legal ):
        # self.body is the text we parse.
        # self.start_index is the current position.

        # First, eat white space and comments
        while self.start_index < len(self.body):
            this_char = self.body[self.start_index]
            if this_char == "/":
                if self.start_index + 1 < len(self.body):
                    next_char = self.body[self.start_index + 1]

                    if next_char == "*":
                    # Scan until we find the end
                        self.start_index += 2
                        pos = self.body.find("*/",self.start_index)
                        if pos == -1:
                            self.start_index = len(self.body)
                            break
                        else:
                            self.start_index = pos + 2
                            continue

                    elif next_char == "/":
                        # Scan until we find the newline
                        self.start_index += 2
                        pos = self.body.find("\n",self.start_index)
                        if pos == -1:
                            self.start_index = len(self.body)
                            break
                        else:
                            self.start_index = pos
                            continue

            if this_char > " ":
                break
            self.start_index += 1

        # Check for end
        if self.start_index == len(self.body):
            return None

        # See what kind of token it is.  Choices are: punctuation, string,
        # symbol, or number
        this_char = self.body[self.start_index]
        self.start_index += 1

        # For ' or ", treat as a string.  We have to concatenate across lines, too
        if this_char == "'" or this_char == '"':
            # Accumulate a string
            the_string = ""
            while self.start_index < len(self.body):
                new_char = self.body[self.start_index]
                if new_char == "\\":
                    self.start_index += 1
                    if self.start_index < len(self.body):
                        the_char = self.body[ self.start_index ]
                        # Deal with special characters
                        if the_char == "n":
                            the_char = "\n"
                        elif the_char == "r":
                            the_char = "\r"
                        elif the_char == "t":
                            the_char = "\t"
                        the_string += the_char
                        self.start_index += 1
                elif new_char == this_char:
                    # Maybe the end of the string, but we should zip past the whitespace
                    # to see if there's more string.
                    self.start_index += 1
                    while self.start_index < len(self.body):
                        skip_char = self.body[self.start_index]
                        if skip_char > " ":
                            break
                        self.start_index += 1
                    if self.start_index == len(self.body):
                        break
                    this_char = self.body[self.start_index]
                    if this_char != "'" and this_char != '"':
                        break
                    self.start_index += 1
                else:
                    the_string += self.body[ self.start_index ]
                    self.start_index += 1
            return JSToken( string_value=the_string )

        # For digits, treat as numbers.
        elif this_char >= "0" and this_char <= "9":
            # concatenate numbers and decimal points; no ieee notation for now
            the_number = this_char
            is_float = False
            while self.start_index < len(self.body):
                new_char = self.body[self.start_index]
                if (new_char < "0" or new_char > "9") and new_char != ".":
                    break
                self.start_index += 1
                if new_char == ".":
                    is_float = True
                the_number += new_char
            if is_float:
                return JSToken( float_value=float(the_number) )
            else:
                return JSToken( int_value=int(the_number) )

        # For characters, eat as IDs
        elif (this_char >= "a" and this_char <= "z") or (this_char >= "A" and this_char <= "Z") or this_char == "_":
            # concatenate characters and digits
            the_symbol = this_char
            while self.start_index < len(self.body):
                new_char = self.body[self.start_index]
                if (new_char < "a" or new_char > "z") and (new_char < "A" or new_char > "Z") and (new_char < "0" or new_char > "9") and new_char != "_":
                    break
                self.start_index += 1
                the_symbol += new_char
            return JSToken( symbol_value=the_symbol )

        # Regexps.
        # This is tricky, because "/" is a legal operator.  We therefore need to process
        # the subsequent string to see if it looks promising.  If that's not good enough,
        # then we'll need to know if we are in a context where a simple "/" is a legal possibility.
        elif this_char == "/" and slash_is_legal == False:
            # Look for the trailing /, and make sure we destuff as we look
            the_regexp = ""
            while self.start_index < len(self.body):
                new_char = self.body[self.start_index]
                self.start_index += 1
                if new_char == "\\" and self.start_index < len(self.body):
                    the_regexp += new_char + self.body[ self.start_index ]
                    self.start_index += 1
                elif new_char == "/":
                    break
                else:
                    the_regexp += new_char

            is_global = False
            is_insensitive = False
            if self.start_index < len(self.body):
                new_char = self.body[self.start_index]
                if new_char == "g":
                    is_global = True
                    self.start_index += 1
                elif new_char == "i":
                    is_insensitive = True
                    self.start_index += 1
            if self.start_index < len(self.body):
                new_char = self.body[self.start_index]
                if new_char == "g":
                    is_global = True
                    self.start_index += 1
                elif new_char == "i":
                    is_insensitive = True
                    self.start_index += 1
            return JSToken( regexp_value=the_regexp, regexp_global=is_global, regexp_insensitive=is_insensitive )

        # Punctuation
        # See if this is a punctuation mark that can have a second character
        the_punc = this_char
        if self.start_index < len(self.body):
            new_char = self.body[self.start_index]
            if this_char == "!" and new_char == "=" or this_char == "=" and new_char == "=" \
                or this_char == ">" and new_char == "=" or this_char == "<" and new_char == "=" \
                or this_char == "&" and new_char == "&" or this_char == "|" and new_char == "|" \
                or this_char == "+" and new_char == "=" or this_char == "-" and new_char == "=" \
                or this_char == "+" and new_char == "+" or this_char == "-" and new_char == "-" \
                or this_char == "*" and new_char == "=" or this_char == "/" and new_char == "=" \
                or this_char == "<" and new_char == "<" or this_char == ">" and new_char == ">":
                self.start_index += 1
                the_punc += new_char
        return JSToken( punc_value=the_punc )
