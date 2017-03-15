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
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import Javascript
import urllib
import urllib2
import httplib
import cookielib
import HTMLParser
import base64
import re
import os
import sys

# Class that describes answers to pop-up dialog boxes that may occur as a result of clicking a link
# or button.  If the answer is not found, this is considered a test error, and an exception is thrown!
class VirtualDialogAnswers:

    def __init__( self ):
        # a dictionary of dialogs, by title, containing the desired answer to click for each
        self.dialogs = { }

    def add_dialog_answer( self, dialog_name, answer_button ):
        self.dialogs[ dialog_name ] = answer_button

    def get_dialog_answer( self, dialog_name ):
        try:
            return self.dialogs[ dialog_name ]
        except:
            raise Exception("No answer found for dialog '%s'" % dialog_name)


# Base class of all form elements
class VirtualFormElement:

    def __init__( self, form_instance ):
        self.form_instance = form_instance

    def get_form( self ):
        return self.form_instance

# Base class of form data elements.  Each of these has an element name, and
# can contribute data to form submission.
class VirtualFormDataElement( VirtualFormElement ):

    def __init__(self, form_instance, element_name ):
        VirtualFormElement.__init__( self, form_instance )
        self.element_name = element_name

    # Get the key which uniquely identifies this element in a form.
    def get_key( self ):
        return self.element_name

    # Optionally add a set of name/value tuples to a sequence to be posted
    def add_variables( self, accumulator ):
        pass

    # Optionally add file tuples to a sequence to be posted
    def add_files( self, accumulator ):
        pass

    # Get the element name
    def get_name( self ):
        return self.element_name

    # Set a property (via javascript)
    # Property value is a JSObject
    def set_property( self, property_name, property_value ):
        raise Exception("Form element '%s' has no such property '%s'" % (self.element_name, property_name))

    # Get a property (via javascript).
    # This returns a JSObject
    def get_property( self, property_name ):
        raise Exception("Form element '%s' has no such property '%s'" % (self.element_name, property_name))

    # Get the type of a property (via javascript)
    def get_property_type( self, property_name ):
        return "undefined"

# Button base class
class VirtualButton( VirtualFormElement ):

    def __init__( self, form_instance, alt, buttontext ):
        VirtualFormElement.__init__( self, form_instance )
        self.buttontext = buttontext
        self.alt = alt

    # Public API

    def click( self ):
        # Only derived classes actually do anything
        pass

    # Private API

    def get_key( self ):
        return self.alt


# Basic button
class VirtualBasicbutton( VirtualButton ):

    def __init__( self, form_instance, alt, buttontext, onclick ):
        VirtualButton.__init__( self, form_instance, alt, buttontext )
        self.onclick = onclick


    def click( self ):
        # Execute the on-click, if any
        self.get_form( ).execute_javascript_expression( self.onclick )


# Since submit buttons are both buttons and form data elements, there's
# a wrapper class needed so that I can put submit buttons in the button
# pool.  This avoids the tester needing to know about what kind of button
# it is; it can be clicked just like any other button.
class VirtualSubmitbuttonWrapper( VirtualButton ):

    def __init__( self, button_reference ):
        assert isinstance( button_reference, VirtualSubmitbutton )
        VirtualButton.__init__( self, button_reference.get_form( ),
                                button_reference.get_alt( ), "Submit" )
        self.button_reference = button_reference

    def click( self ):
        # Pass the click off to the actual button
        self.button_reference.click( )


# Submit button.  This is the data element version of the submit button.
# It's also wrapped by
class VirtualSubmitbutton( VirtualFormDataElement ):

    def __init__( self, form_instance, name, value, alt, onclick ):
        VirtualFormDataElement.__init__( self, form_instance, name )
        self.alt = alt
        self.value = value
        self.clicked = False
        self.onclick = onclick

    def get_alt( self ):
        return self.alt

    def click( self ):
        self.clicked = True
        # Fire off javascript, or if not there, just submit form
        result = self.get_form( ).execute_javascript_expression( self.onclick )
        if result != None:
            if result.bool_value( ):
                self.get_form( ).submit( )
        else:
            self.get_form( ).submit( )
        self.clicked = False
        pass

    # Get the key which uniquely identifies this element in a form.
    def get_key( self ):
        return VirtualFormDataElement.get_key( self ) + "=" + self.value

    # Optionally add a set of name/value tuples to a sequence to be posted
    def add_variables( self, accumulator ):
        if self.clicked:
            accumulator.append( ( self.get_name(), self.value ) )

# Hidden
class VirtualHiddenField( VirtualFormDataElement ):

    def __init__( self, form_instance, name, value ):
        VirtualFormDataElement.__init__( self, form_instance, name )
        assert isinstance( value, unicode ) or isinstance( value, str )
        self.value = { value:value }

    def get_specified_value( self ):
        return_value = ""
        for value in self.value.keys():
            if return_value == "":
                return_value = value
            else:
                return_value = ";" + value
        return return_value

    def add_value( self, new_value ):
        self.value[ new_value ] = new_value

    # Optionally add a set of name/value tuples to a sequence to be posted
    def add_variables( self, accumulator ):
        for value in self.value.keys():
            accumulator.append( ( self.get_name(), value ) )

    # Set a property (via javascript)
    def set_property( self, property_name, property_value ):
        if property_name == "value":
            assert isinstance( property_value, Javascript.JSObject )
            new_value = property_value.str_value( )
            self.value = { new_value:new_value }
        else:
            VirtualFormDataElement.set_property( self, property_name, property_value )

    # Get a property (via javascript)
    def get_property( self, property_name ):
        if property_name == "type":
            return Javascript.JSString("hidden")
        if property_name == "value":
            return Javascript.JSString( self.get_specified_value() )
        else:
            return VirtualFormDataElement.get_property( self, property_name )

    def get_property_type( self, property_name ):
        if property_name == "type":
            return "string"
        if property_name == "value":
            return "string"
        else:
            return VirtualFormDataElement.get_property_type( self, property_name )

# File
class VirtualFileBrowser( VirtualFormDataElement ):

    def __init__( self, form_instance, name ):
        VirtualFormDataElement.__init__( self, form_instance, name )
        self.filename = ""
        self.content_type = None

    # Public part of interface

    # Set the upload file
    def setfile( self, filename, content_type ):
        assert isinstance( filename, str ) or isinstance( filename, unicode )
        assert isinstance( content_type, str ) or isinstance( content_type, unicode )
        self.filename = filename
        self.content_type = content_type

    # Private part of interface

    # Optionally add a set of file tuples to a sequence to be posted
    def add_files( self, accumulator ):
        # Attempt to do the file upload, unless blank
        if self.filename != None and len(self.filename) > 0:
            accumulator.append( ( self.get_name(), self.filename, read_file(self.filename), self.content_type ) )

    # Get a property (via javascript)
    def get_property( self, property_name ):
        if property_name == "value":
            return Javascript.JSString( self.filename )
        else:
            return VirtualFormDataElement.get_property( self, property_name )

    # Get a property type (via javascript)
    def get_property_type( self, property_name ):
        if property_name == "value":
            return "string"
        else:
            return VirtualFormDataElement.get_property_type( self, property_name )

# Read a specified file entirely into a "string"
def read_file( filename ):
    f = open( filename, "rb" )
    try:
        return f.read()
    finally:
        f.close()


# Checkbox
class VirtualCheckbox( VirtualFormDataElement ):

    def __init__( self, form_instance, name, value, selected ):
        VirtualFormDataElement.__init__( self, form_instance, name )
        assert isinstance( value, unicode ) or isinstance( value, str )
        self.value = value
        self.selected = selected
        self.bodytext = None

    # Public part of interface

    # Select this checkbox
    def select( self ):
        if self.selected == False:
            self.selected = True

    # Deselect this checkbox
    def deselect( self ):
        if self.selected:
            self.selected = False

    # Private part of interface

    # Get the key which uniquely identifies this element in a form.
    def get_key( self ):
        return VirtualFormDataElement.get_key( self ) + "=" + self.value

    # Optionally add a set of name/value tuples to a sequence to be posted
    def add_variables( self, accumulator ):
        if self.selected:
            accumulator.append( ( self.get_name(), self.value ) )

    def set_bodytext( self, bodytext ):
        assert isinstance( bodytext, unicode ) or isinstance( bodytext, str )
        self.bodytext = bodytext

    def get_property( self, property_name ):
        if property_name == "checked":
            return Javascript.JSBoolean(self.selected)
        return VirtualFormDataElement.get_property( self, property_name )

    def get_property_type( self, property_name ):
        if property_name == "checked":
            return "boolean"
        return VirtualFormDataElement.get_property_type( self, property_name )

# Radio
class VirtualRadiobutton( VirtualFormDataElement ):

    def __init__( self, form_instance, name, value, selected ):
        VirtualFormDataElement.__init__( self, form_instance, name )
        assert isinstance( value, str ) or isinstance( value, unicode )
        self.value = value
        self.selected = selected
        self.bodytext = None

    # Public part of interface

    # Select this radio button
    def select( self ):
        if self.selected == False:
            self.get_form( ).clear_radio_buttons( self.get_name( ) )
            self.selected = True

    # Private part of interface

    def set_bodytext( self, bodytext ):
        self.bodytext = bodytext

    # Deselect this radio button.  This happens as a side-effect
    # of selecting another one that has the same name.
    def deselect( self ):
        if self.selected == True:
            self.selected = False

    # Get the key which uniquely identifies this element in a form.
    def get_key( self ):
        return VirtualFormDataElement.get_key( self ) + "=" + self.value

    # Optionally add a set of name/value tuples to a sequence to be posted
    def add_variables( self, accumulator ):
        if self.selected:
            accumulator.append( ( self.get_name(), self.value ) )

# Select box element
class SelectboxElement:
    def __init__( self, value, body_text, is_selected ):
        self.value = value
        self.body_text = body_text
        self.is_selected = is_selected

    def get_value( self ):
        return self.value

    def get_body_text( self ):
        return self.body_text

    def check_selected( self ):
        return self.is_selected

    def set_selected( self, is_selected ):
        self.is_selected = is_selected

# Javascript object representing an option that's tied back to a VirtualSelectBox element
class JSOptionValue (Javascript.JSObject ):

    def __init__( self, parent ):
        Javascript.JSObject.__init__( self )
        self.parent = parent

    def get_value( self, member_name ):
        if member_name == "text":
            return Javascript.JSString(self.parent.get_body_text())
        elif member_name == "value":
            return Javascript.JSString(self.parent.get_value())
        elif member_name == "selected":
            printval = "no"
            if self.parent.is_selected:
                printval = "yes"
            #print "Checking whether option %s is selected: %s" % (self.parent.value,printval)
            return Javascript.JSBoolean(self.parent.check_selected())
        return Javascript.JSObject.get_value( self, member_name )

    def set_value( self, member_name, value ):
        if member_name == "text":
            self.parent.set_body_text(value.str_value())
        elif member_name == "value":
            self.parent.set_value(value.str_value())
        elif member_name == "selected":
            self.parent.set_selected(value.bool_value())
        else:
            Javascript.JSObject.set_value( self, member_name, value )

# Javascript object representing a list of options - tied back to a VirtualSelectBox
class JSOptionList( Javascript.JSObject ):

    def __init__( self, parent_select_box ):
        Javascript.JSObject.__init__( self )
        self.parent_select_box = parent_select_box

    def get_value( self, member_name ):
        # This object should behave like an array, so interpret the member name to be
        # an index value
        index = int(member_name)
        assert index >= 0
        return self.parent_select_box.get_option_object( index )

    def set_value( self, member_name, value ):
        # This object should behave like an array, so interpret the member name to be
        # an index value
        index = int(member_name)
        assert index >= 0
        self.parent_select_box.set_option_object( index, value )

# Single/multi select
class VirtualSelectbox( VirtualFormDataElement ):

    def __init__( self, form_instance, name, multi ):
        VirtualFormDataElement.__init__( self, form_instance, name )
        self.multi = multi
        self.option_value_list = [ ]

    # Public api

    # Select a value (without CTRL button).
    # This works like a browser in that selecting in this way turns
    # off all other current selections.
    def select_value( self, selected_value ):
        seen_value = False
        for option in self.option_value_list:
            if option.get_value() == selected_value:
                if seen_value:
                    raise Exception("The selectbox value '%s' appears more than once in '%s'" % (selected_value,self.get_name()))
                seen_value = True
                option.set_selected( True )
            else:
                option.set_selected( False )
        if seen_value == False:
            raise Exception("The selectbox value '%s' doesn't exist in '%s'" % (selected_value,self.get_name()))

    # Select a value using a regular expression (without CTRL button)
    def select_value_regexp( self, selected_value_regexp ):
        regexp = re.compile( selected_value_regexp, 0 )
        seen_value = False
        for option in self.option_value_list:
            option_value = option.get_value()
            mo = regexp.search( option_value )
            if mo != None:
                if seen_value:
                    raise Exception("Selectbox '%s' has more than one value matching '%s'" % (self.get_name(),selected_value_regexp))
                seen_value = True
                option.set_selected( True )
            else:
                option.set_selected( False )
        if seen_value == False:
            raise Exception("Selectbox '%s' does not have a value matching '%s'" % (self.get_name(),selected_value_regexp))

    # CTRL-select a value.
    # For multiselect boxes, this adds a new selection to those already
    # chosen.  For non-multi boxes, it works just like select_value.
    def multi_select_value( self, selected_value ):
        if self.multi == False:
            self.select_value( selected_value )
        else:
            seen_value = False
            for option in self.option_value_list:
                if option.get_value() == selected_value:
                    if seen_value:
                        raise Exception("Selectbox '%s' has more than one value matching '%s'" % (self.get_name(),selected_value))
                    seen_value = True
                    # Toggle
                    option.set_selected( option.check_selected() == False )
            if seen_value == False:
                raise Exception("Selectbox '%s' does not have a value matching '%s'" % (self.get_name(),selected_value))

    # Private api

    # Add a legal selection to the selection list
    def add_selection( self, value, bodytext, isselected ):
        self.option_value_list.append(SelectboxElement( value, bodytext, isselected ))

    # Optionally add a set of name/value tuples to a sequence to be posted
    def add_variables( self, accumulator ):
        for option in self.option_value_list:
            if option.check_selected( ):
                accumulator.append( ( self.get_name(), option.get_value() ) )

    # Get a property (via javascript)
    def get_property( self, property_name ):
        if property_name == "type":
            if self.multi:
                return Javascript.JSString("select-multiple")
            else:
                return Javascript.JSString("select")
        elif property_name == "value":
            value = ""
            for option in self.option_value_list:
                if option.check_selected( ):
                    if len(value) > 0:
                        value += ";"
                    value += option.get_value()
            return Javascript.JSString( value )
        elif property_name == "options":
            # Return a JSArray describing the options objects underlying this selectbox
            return JSOptionList( self )
        elif property_name == "length":
            # Return as JSNumber describing the length of the array
            return Javascript.JSNumber(len(self.option_value_list))
        elif property_name == "selectedIndex":
            # Return the first selected index as a JSNumber
            for index in range(len(self.option_value_list)):
                if self.option_value_list[index].check_selected( ):
                    return Javascript.JSNumber(index)
            return Javascript.JSNumber(-1)
        else:
            return VirtualFormDataElement.get_property( self, property_name )

    # Get a property type (via javascript)
    def get_property_type( self, property_name ):
        if property_name == "type":
            return "string"
        elif property_name == "value":
            return "string"
        elif property_name == "options":
            # Return a JSArray describing the options objects underlying this selectbox
            return "array"
        elif property_name == "length":
            # Return as JSNumber describing the length of the array
            return "number"
        elif property_name == "selectedIndex":
            return "number"
        else:
            return VirtualFormDataElement.get_property_type( self, property_name )

    # Get an option object
    def get_option_object( self, index ):
        assert index < len(self.option_value_list)
        return JSOptionValue( self.option_value_list[index] )

    # Set an option object
    def set_option_object( self, index, object ):
        assert isinstance(object,Javascript.JSObject)
        if isinstance(object,Javascript.JSNull):
                # It's a delete; remove the specified entry wherever it is found
                #print "Deleting index %d from %s" % (index,self.get_name())
            if index >= len(self.option_value_list):
                return
            del self.option_value_list[index]
        else:
            # Grab the text and value attributes
            text = object.get_value( "text" ).str_value()
            value = object.get_value( "value" ).str_value()
            #print "Setting index %d for %s to text %s value %s" % (index,self.get_name(),text,value)
            # Set this at the current index
            if index >= len(self.option_value_list):
                self.add_selection( value, text, False )
            # Create a new selectbox element
            element = SelectboxElement( value, text, False )
            self.option_value_list[index] = element

# Text/password field
class VirtualTextarea( VirtualFormDataElement ):

    def __init__( self, form_instance, name ):
        VirtualFormDataElement.__init__( self, form_instance, name )
        self.textvalue = ""

    # Public api

    # Set text value
    def set_value( self, text_value ):
        assert isinstance( text_value, unicode ) or isinstance( text_value, str )
        self.textvalue = text_value

    # Private API

    # Optionally add a set of name/value tuples to a sequence to be posted
    def add_variables( self, accumulator ):
        accumulator.append( ( self.get_name( ), self.textvalue ) )

    # Set a property (via javascript)
    def set_property( self, property_name, property_value ):
        if property_name == "value":
            assert isinstance( property_value, Javascript.JSObject )
            self.textvalue = property_value.str_value( )
        else:
            VirtualFormDataElement.set_property( self, property_name, property_value )

    # Get a property (via javascript)
    def get_property( self, property_name ):
        if property_name == "value":
            return Javascript.JSString( self.textvalue )
        elif property_name == "focus":
            return JSFocusMethod( self )
        else:
            return VirtualFormDataElement.get_property( self, property_name )

    # Get a property type (via javascript)
    def get_property_type( self, property_name ):
        if property_name == "value":
            return "string"
        else:
            return VirtualFormDataElement.get_property_type( self, property_name )

# Class that describes a virtual form.  Each form has an identifier (the form name), plus form elements
# that live in the form.
class VirtualForm:

    def __init__( self, window_instance, name, action_url, method ):
        # These elements all have scrapable data, and are organized by
        # key (which comes from the element).
        self.data_elements = { }
        self.javascript_data_elements = { }
        self.window_instance = window_instance
        self.form_name = name
        self.action_url = action_url
        self.method = method

    # Public API

    # Get the form name
    def get_name( self ):
        return self.form_name

    # Find a file browser
    # Matches data variable name.
    def find_filebrowser( self, data_name ):
        try:
            value = self.data_elements[ data_name ]
            assert isinstance( value, VirtualFileBrowser )
            return value
        except:
            raise Exception("Can't find filebrowser %s on form %s for url %s" % (data_name,self.form_name,self.window_instance.get_current_url( )))


    # Find a checkbox
    # Matches checkbox data variable name, and value.
    # Returns a VirtualCheckbox object, or None if not found.
    def find_checkbox( self, data_name, value ):
        key = data_name + "=" + value
        try:
            value = self.data_elements[ key ]
            assert isinstance( value, VirtualCheckbox )
            return value
        except:
            raise Exception("Can't find checkbox %s:%s on form %s for url %s" % (data_name,value,self.form_name,self.window_instance.get_current_url( )))

    # Find a radio button
    # Matches radio button data variable name, and value.
    # Returns a VirtualRadiobutton object, or None if not found.
    def find_radiobutton( self, data_name, value ):
        key = data_name + "=" + value
        try:
            value = self.data_elements[ key ]
            assert isinstance( value, VirtualRadiobutton )
            return value
        except:
            raise Exception("Can't find radiobutton %s:%s on form %s for url %s" % (data_name,value,self.form_name,self.window_instance.get_current_url()))

    # Find a selection box
    # Matches based on the data variable name alone.
    # Returns a VirtualSelectbox object, or None if not found.
    def find_selectbox( self, data_name ):
        try:
            value = self.data_elements[ data_name ]
            assert isinstance( value, VirtualSelectbox )
            return value
        except:
            raise Exception("Can't find selectbox %s on form %s for url %s" % (data_name,self.form_name,self.window_instance.get_current_url()))

    # Find a textarea/password field
    # Matches based on the data variable name alone.
    # Returns a VirtualTextarea object, or None if not found.
    def find_textarea( self, data_name ):
        try:
            value = self.data_elements[ data_name ]
            assert isinstance( value, VirtualTextarea )
            return value
        except:
            raise Exception("Can't find textarea %s on form %s for url %s" % (data_name,self.form_name,self.window_instance.get_current_url()))

    # Get the action URL
    def get_action_url( self ):
        return self.action_url

    # Set the action URL
    def set_action_url( self, newurl ):
        self.action_url = newurl

    # Private API

    # Find an element based on its data name.
    # This will return the FIRST matching checkbox or radio button, which seems to be the
    # browser convention.
    def find_element_by_dataname( self, data_name ):
        return self.javascript_data_elements[ data_name ]

    # Execute javascript expression in the form context.
    # Returns a JSObject representing the result.
    def execute_javascript_expression( self, javascript ):
        return self.window_instance.execute_javascript_expression( javascript )

    # Add an element to this form.
    def add_element( self, element ):
        assert isinstance( element, VirtualFormDataElement )
        self.data_elements[ element.get_key( ) ] = element
        if not self.javascript_data_elements.has_key( element.get_name( ) ):
            self.javascript_data_elements[ element.get_name( ) ] = element

    # This method is called just before a radio button is selected.
    # It must deselect all radio buttons that share this element name.
    def clear_radio_buttons( self, element_name ):
        # Go through all the elements
        for element_key, element_value in self.data_elements.iteritems( ):
            if element_name == element_value.get_name( ):
                assert isinstance( element_value, VirtualRadiobutton )
                element_value.deselect( )

    # This method does the nuts and bolts of submitting a form - basically,
    # gathering data from all the form elements and stringing it together into
    # a list of form variables and their values, then sending it on to the window
    # (and hence to the browser instance) for posting.
    def submit( self ):
        # Go through all elements and scrape up current values, and send those
        # to the action url

        # First, go through the elements and gather up a variable and file set
        variables = [ ]
        files = [ ]
        for element_key, element_value in self.data_elements.iteritems( ):
            element_value.add_variables( variables )
            element_value.add_files( files )

        # Pass these off to the window
        self.window_instance.execute_action( self.method, variables, files, self.action_url )

# Class that describes a link in a virtual browser window.
class VirtualLink:

    def __init__( self, window_instance, alt, url, onclick ):
        self.window_instance = window_instance
        self.alt = alt
        self.linktext = None
        self.onclick = onclick
        self.url = url

    # Public part of interface

    # Click this virtual link
    def click( self ):
        result = self.window_instance.execute_javascript_expression( self.onclick )
        if result != None:
            if result.bool_value( ):
                # Take link
                self.window_instance.execute_link( self.url )
        else:
            self.window_instance.execute_link( self.url )

    # Private part of interface

    def get_alt( self ):
        return self.alt

    def set_bodytext( self, bodytext ):
        self.linktext = bodytext

# Dummy request (so we can use cookiejar)
class DummyRequest:
    """
    Dummy request (for interfacing with cookiejar).
    """

    def __init__( self, protocol, host, url ):
        self.protocol = protocol
        self.host = host
        self.url = url
        self.headers = {}

    def has_header( self, name ):
        return name in self.headers

    def add_header( self, key, val ):
        self.headers[key.capitalize()] = val

    def add_unredirected_header(self, key, val):
        self.headers[key.capitalize()] = val

    def is_unverifiable( self ):
        return True

    def get_type( self ):
        return self.protocol

    def get_full_url( self ):
        return self.url

    def get_header( self, header_name, default=None ):
        return self.headers.get( header_name, default )

    def get_host( self ):
        return self.host

    get_origin_req_host = get_host

    def get_headers( self ):
        return self.headers

# Class that describes a virtual browser window.  Each virtual window has some set of forms and links,
# as well as a set of dialog boxes (which can be popped up due to various actions, and dismissed
# by virtual user activity)
class VirtualWindow:

    def __init__( self, browser_instance, window_name, data, parent, current_url ):
        print >>sys.stderr, "Loading window '%s' with data from url %s" % (window_name, current_url)
        self.links = { }
        self.buttons = { }
        self.forms = { }
        self.window_name = window_name
        self.data = data
        #print >>sys.stderr, data
        self.parent = parent
        self.browser_instance = browser_instance
        self.is_open = True
        self.jscontext = Javascript.JSScope( enclosing_scope=None )
        self.dialog_answers = None
        self.current_url = current_url

        # Now, assert javascript objects into the current scope to permit
        # Javascript to work.

        # Need methods for:
        # confirm( )
        # alert( )
        # eval( )
        # parseInt( )

        self.jscontext.define_value( "confirm", JSConfirmMethod( self ) )
        self.jscontext.define_value( "alert", JSAlertMethod( self ))
        self.jscontext.define_value( "eval", JSEvalMethod( self ))
        self.jscontext.define_value( "parseInt", JSParseIntMethod( self ))

        # Need built-in objects for:
        # document (with form properties and form element properties beneath that).
        # Also, need the shortcut entered, which is a property that's just the form name.
        # First, create the document object.
        self.jsdocobject = JSDocObject( )
        self.jscontext.define_value( "document", self.jsdocobject )

        # Window object
        self.jswindowobject = JSWindowObject( self )
        self.jscontext.define_value( "window", self.jswindowobject )

        # Finally, need built-in "Option" class
        jsoptionclassdef = JSOptionClassDef( )
        self.jscontext.define_value( "Option", jsoptionclassdef )

        # Parse the data
        parser = VirtualActionParser( self )
        parser.feed( data )
        parser.close( )

        """ for form_name, form_object in self.forms.iteritems( ):
            # Build a javascript object representing the form
            jsobject = JSFormObject( form_object )
            # Add this object to the doc object
            self.jsdocobject.add_form( form_name, jsobject )
            # Add this object to the main context
            self.jscontext.define_value( form_name, jsobject )
        """

    # Public part of interface

    # Look for pattern
    def is_present( self, regular_expression ):
        reobject = re.compile( regular_expression )
        mo = reobject.search( self.data )
        return mo != None

    # Look for a specific match in the page data, and return the value of the specified group
    def find_match( self, regular_expression, group=0 ):
        reobject = re.compile( regular_expression )
        mo = reobject.search( self.data )
        if mo == None:
            raise Exception("Pattern %s not found in page %s (%s)" % (regular_expression,self.current_url,self.data))
        return mo.group(group)

    # Look for a specific match in the page data, and return the value of the specified group
    def find_match_no_newlines( self, regular_expression, group=0 ):
        reobject = re.compile( regular_expression )
        mo = reobject.search( " ".join(self.data.split( )) )
        if mo == None:
            raise Exception("Pattern %s not found in page %s (%s)" % (regular_expression,self.current_url,self.data))
        return mo.group(group)

    # Make sure there is NOT a match of the specified kind.
    def check_no_match( self, regular_expression ):
        reobject = re.compile( regular_expression )
        mo = reobject.search( self.data )
        if mo != None:
            raise Exception("Pattern %s was erroneously found in page %s (%s)" % (regular_expression,self.current_url,self.data))

    # Set the current dialog answers, in case there are virtual dialog popups
    def set_dialog_answers( self, dialog_answers ):
        assert dialog_answers == None or isinstance( dialog_answers, VirtualDialogAnswers )
        self.dialog_answers = dialog_answers

    # For all of the operations that refer to form elements, the elements are
    # referenced by form name and something which identifies the element within
    # the form, which is element specific.  For example, a checkbox is identified
    # by the text and html between the <input> and </input> tags.  This is presumed to be
    # unique for the form.  Since an exact match is often difficult to generate, the
    # matches are passed in as regular expressions whenever arbitrary HTML needs to be
    # matched.
    #
    # For links, the link text and alt text are used for identification.  Regexps are
    # allowed for the link text matching.

    # Find a link
    # Needs to match the alt text, which must be unique on the page.
    # Returns a VirtualLink object, or None if not found.
    def find_link( self, alt ):
        try:
            return self.links[ alt ]
        except:
            raise Exception("Can't find link %s on page %s" % (alt,self.current_url))

    # Find a form
    # Needs to match the form name.  Returns a VirtualForm object, or None.
    def find_form( self, form_name ):
        try:
            return self.forms[ form_name ]
        except:
            raise Exception("Can't find form %s on page %s" % (form_name,self.current_url))

    # Find a button
    # Matches based on the button's alt text, which
    # must be unique.  Returns a VirtualButton object, or None if not
    # found.  The button may be of many different types (e.g. submit
    # buttons, plain buttons, etc).
    def find_button( self, alt ):
        try:
            return self.buttons[ alt ]
        except:
            raise Exception("Can't find button %s on page %s" % (alt,self.current_url))

    # Close this window.  This is meant to correspond directly to closing the window
    # in the browser.
    def close_window( self ):
        # This may fire off scripts someday, but today it just marks the window as dead
        self.is_open = False
        self.browser_instance.delete_window( self.window_name )

    # Get the current url for this window
    def get_current_url( self ):
        return self.current_url

    # Get the data for this window
    def get_data( self ):
        return self.data

    # Private part of interface

    # Initialize; execute whatever startup scripts and side-effect loading is needed
    def initialize( self ):
        # Probably I ought to implement frameset handling at least, but right now even
        # that is not needed
        is_open = True

    # Get the parent window
    def get_parent_window( self ):
        return self.parent

    # Get dialog answers in place
    def get_dialog_answers( self ):
        return self.dialog_answers

    # Check if a symbol exists in the current javascript context
    def check_exists( self, symbol_name ):
        try:
            self.jscontext.get_value( symbol_name )
            return True
        except:
            return False

    # Execute javascript expression in the window context.
    # Returns a JSObject representing the result.
    def execute_javascript_expression( self, javascript ):
        # All objects, methods, etc. have already been asserted into the javascript context,
        # which gives javascript the access it needs to document objects and built-in methods.
        # So we just need to execute against it.

        if javascript != None and javascript.lower( ).startswith("javascript:"):
            tokenstream = Javascript.JSTokenStream( javascript[ len("javascript:") : len(javascript) ] )
            return tokenstream.evaluate_expr( self.jscontext, "HTML" )
        return None

    # Do a post or a get into the current window
    def execute_action( self, method, parameters, files, url ):
        if self.is_open == False:
            raise Exception("Cannot execute action %s on already closed window %s" % ( url, self.window_name ) )
        # Send the url off to the browser instance to load
        return self.browser_instance.execute_action( self.window_name, method, parameters, files, self.resolve( url ) )

    # Do a get with a preformed url.
    def execute_link( self, url ):
        if self.is_open == False:
            raise Exception("Cannot execute link %s on already closed window %s" % (url,self.window_name) )
        return self.browser_instance.execute_link( self.window_name, self.resolve( url ) )

    # Add a link
    def add_link( self, linkobject ):
        assert isinstance( linkobject, VirtualLink )
        self.links[ linkobject.get_alt( ) ] = linkobject

    # Add a form
    def add_form( self, formobject ):
        assert isinstance( formobject, VirtualForm )
        form_name = formobject.get_name( )
        self.forms[ form_name ] = formobject
        jsobject = JSFormObject( formobject )
        # Add this object to the doc object
        self.jsdocobject.add_form( form_name, jsobject )
        # Add this object to the main context
        self.jscontext.define_value( form_name, jsobject )

    # Add a button
    def add_button( self, buttonobject ):
        assert isinstance( buttonobject, VirtualButton )
        self.buttons[ buttonobject.get_key( ) ] = buttonobject

    # Accept javascript definitions etc.
    def accept_javascript( self, javascript_text ):
        javascript_text = javascript_text.lstrip( ).rstrip( )
        if javascript_text.startswith("<!--"):
            javascript_text = javascript_text[4:len(javascript_text)]
        if javascript_text.endswith("//-->"):
            javascript_text = javascript_text[0:len(javascript_text)-5]
        jstokens = Javascript.JSTokenStream( javascript_text )
        jstokens.evaluate_statement_list( self.jscontext )

    # Get the answer to a dialog question.
    def get_answer( self, question, default_answer ):
        # Look in the dialog_answers structure for some guidance
        # MHL
        return default_answer

    # Resolve a (potentially relative) url into an absolute url with full qualification
    def resolve( self, url ):
        protocol = self.current_url.index("://") + 3
        if url.find("://") != -1:
            return url
        elif url.startswith("/"):
            # Relative to domain
            domain = self.current_url.index("/",protocol)
            return self.current_url[0:domain] + url
        else:
            endguy = self.current_url.rindex("/")+1
            return self.current_url[0:endguy] + url

# Class that describes a virtual browser instance, with various windows and their associated
# alerts/popups
class VirtualBrowser:

    def __init__( self, username=None, password=None, win_host=None, language="en-US" ):
        self.window_set = { }
        self.username = username
        self.password = password
        self.win_host = win_host
        self.language = language
        self.cookiejar = cookielib.CookieJar()
        if win_host == None and username != None:
            # Set up basic auth
            pass
            #self.urllibopener = urllib2.build_opener( urllib2.HTTPHandler ( ) )
        elif win_host != None and username != None:
            # Proxy-based auth
            # MHL
            raise Exception("Feature not yet implemented")
        else:
            # Use standard opener
            pass
            #self.urllibopener = urllib2.build_opener( urllib2.HTTPHandler ( ) )

    # Public part of the Virtual Browser interface

    # Send the main window to a specific URL
    def load_main_window( self, url, initial_dialog_answers=None ):
        self.build_window( "", self.fetch_data_with_get( url ), None, url, initial_dialog_answers )

    # Find a specific window by name.  Use name=None
    # for main window.  Returns a VirtualBrowserWindow
    # object, or None if the window doesn't exist.
    def find_window( self, window_name="" ):
        try:
            return self.window_set[ window_name ]
        except:
            raise Exception("Can't find existing window %s" % window_name)

    # Private part of the Virtual Browser interface

    # Delete a window
    def delete_window( self, window_name ):
        del self.window_set[ window_name ]

    # Reload an existing window
    def reload_window( self, window_name, window_data, full_url ):
        old_window = self.find_window( window_name )
        old_window.close_window( )
        self.build_window( window_name, window_data, old_window.get_parent_window( ), full_url, old_window.get_dialog_answers( ) )

    # Create a window, with a specific parent and data, and register it.
    # If the window already exists, it will be replaced.
    def build_window( self, window_name, window_data, parent_window, current_url, initial_dialog_answers=None ):
        # Parse the data to create a window, with the specified parent virtual window
        new_window = VirtualWindow( self, window_name, window_data, parent_window, current_url )
        # Put the new window into the window set.  This may well replace an existing window.
        self.window_set[ window_name ] = new_window
        # Now execute start up stuff from that window (when frames are implemented, this is where they would go)
        new_window.set_dialog_answers( initial_dialog_answers )
        new_window.initialize( )

    # Read a url using all the connection parameters, cookies, authentication info etc. available.
    # Creates a new window object accordingly.
    def execute_action( self, window_name, method, parameters, files, url ):
        window_data = None
        if method == "GET":
            if len(files) > 0:
                raise Exception("File controls found in GET submit, for url '%s'" % url)
            # Need to assemble parameters and tack them onto url
            parameter_string = urllib.urlencode( parameters )
            fullurl = url + "?" + parameter_string
            # Invoke!
            window_data = self.fetch_data_with_get( fullurl )
        elif method == "POST":
            if len(files) > 0:
                raise Exception("File controls found in POST submit, for url '%s'" % url)
            # Assemble parameters into form post
            window_data = self.fetch_data_with_post( parameters, url )
        elif method == "MULTIPART":
            # Assemble parameters into multipart form post
            window_data = self.fetch_data_with_multipart_post( parameters, files, url )
        else:
            raise Exception("Unknown action method: %s" % method)

        # Create a new window object with the result, and save it
        self.reload_window( window_name, window_data, url )

    # Read a preformed url into a window using get.
    def execute_link( self, window_name, url ):
        window_data = self.fetch_data_with_get( url )
        self.reload_window( window_name, window_data, url )

    """
    def fetch_and_decode( self, req ):
        f = self.urllibopener.open( req )
        fetch_info = f.info()
        encoding = "iso-8859-1"
        if fetch_info != None and fetch_info.has_key("Content-type"):
            content_type = fetch_info["Content-type"]
            charset_index = content_type.find("charset=")
            if charset_index != -1:
                encoding = content_type[charset_index+8:len(content_type)]
        return f.read( ).decode(encoding)
    """

    # Read a url with get.  Returns the data as a string.
    def fetch_data_with_get( self, url ):
        print >> sys.stderr, "Getting url '%s'..." % url
        return self.talk_to_server(url)
        """
        req = urllib2.Request( url )
        if self.language != None:
            req.add_header("Accept-Language", self.language)
        if self.username != None:
            base64string = base64.encodestring('%s:%s' % (self.username, self.password))[:-1]
            req.add_header("Authorization", "Basic %s" % base64string)
        # Add cookies appropriate to domain
        # MHL - not yet implemented
        # req.add_header('Referer', 'http://www.python.org/')
        return self.fetch_and_decode( req )
        """

    # Read a url with post.  Pass the parameters as an array of ( name, value ) tuples.
    def fetch_data_with_post( self, parameters, url ):
        paramstring = urllib.urlencode( parameters, doseq=True )
        print >> sys.stderr, "Posting url '%s' with parameters '%s'..." % (url, paramstring)
        return self.talk_to_server( url, method="POST", body=paramstring, content_type="application/x-www-form-urlencoded" )
        """
        req = urllib2.Request( url, paramstring )
        if self.language != None:
            req.add_header("Accept-Language", self.language)
        if self.username != None:
            base64string = base64.encodestring('%s:%s' % (self.username, self.password))[:-1]
            req.add_header("Authorization", "Basic %s" % base64string)
        # Add cookies by domain
        # MHL
        return self.fetch_and_decode( req )
        """

    # Private method to post using multipart forms
    def fetch_data_with_multipart_post( self, parameters, files, url ):

        paramstring = urllib.urlencode( parameters, doseq=True )
        filecount = 0
        if files != None:
            filecount = len(files)
        print >> sys.stderr, "Multipart posting url '%s' with parameters '%s' and %d files..." % (url, paramstring, filecount)

        """
        Post fields and files to an http host as multipart/form-data.
        fields is a sequence of (name, value) elements for regular form fields.
        files is a sequence of (name, filename, value, content_type) elements for data to be uploaded as files
        Return the server's response page.
        """
        content_type, body = encode_multipart_formdata(parameters, files)
        return self.talk_to_server(url, method="POST", content_type=content_type, body=body)

    def talk_to_server(self, url, method="GET", content_type=None, body=None):
        
        # Redirection loop
        while True:

            # Turn URL into protocol, host, and selector
            urlpieces = url.split("://")
            protocol = urlpieces[0]
            uri = urlpieces[1]
            # Split uri at the first /
            uripieces = uri.split("/")
            host = uripieces[0]
            selector = uri[len(host):len(uri)]

            if protocol == "http":
                h = httplib.HTTPConnection(host)
            elif protocol == "https":
                h = httplib.HTTPSConnection(host)
            else:
                raise Exception("Unknown protocol: %s" % protocol)

            h.connect()
            try:
                # Set the request type and url
                h.putrequest(method, selector)

                # Set the content type and length
                if content_type != None:
                    h.putheader("content-type", content_type)
                if body != None:
                    h.putheader("content-length", str(len(body)))

                # Add cookies by domain.  To do this with httplib and cookielib,
                # we create a dummy urllib2 request.
                urllib2_request = DummyRequest( protocol, host, url )

                # add cookies to fake request
                self.cookiejar.add_cookie_header( urllib2_request )
                
                # apply cookie headers to actual request
                for header in urllib2_request.get_headers().keys():
                    header_value = urllib2_request.get_headers()[header]
                    h.putheader( header, header_value )

                if self.language != None:
                    h.putheader("Accept-Language", self.language)

                # Add basic auth credentials, if needed.
                if self.username != None:
                    base64string = base64.encodestring("%s:%s" % (self.username, self.password))[:-1]
                    h.putheader("Authorization", "Basic %s" % base64string)

                h.endheaders()

                # Send the body
                if body != None:
                    h.send(body)
                response = h.getresponse()
                status = response.status
                headers = response.getheaders()
                encoding = "iso-8859-1"
                content_type = response.getheader("Content-type","text/html; charset=iso-8859-1")
                charset_index = content_type.find("charset=")
                if charset_index != -1:
                    encoding = content_type[charset_index+8:len(content_type)]

                value = response.read().decode(encoding)

                # HACK: pretend we're urllib2 response for cookielib
                response.info = lambda : response.msg

                # read and store cookies from response
                self.cookiejar.extract_cookies(response, urllib2_request)

                # If redirection, go around again
                if status == 301 or status == 302:
                    # Redirection!  New url to process.
                    location_header = response.msg.getheader("location")
                    if location_header != None:
                        print >>sys.stderr, "Redirecting from url '%s' to url '%s'..." % ( url, location_header )
                        url = location_header
                        continue
                    else:
                        raise Exception("Missing redirection location header")

                # If NOT a redirection, handle it.
                if status != 200:
                    raise Exception("Received an error response %d from url: '%s'" % (status,url) )

                return value
            finally:
                h.close()

# Static method for multipart encoding
def encode_multipart_formdata(fields, files):
    """
    fields is a sequence of (name, value) elements for regular form fields.
    files is a sequence of (name, filename, value, content_type) elements for data to be uploaded as files
    Return (content_type, body)
    """
    import array
    body = array.array('B')
    BOUNDARY = "----------ThIs_Is_tHe_bouNdaRY_$"
    CRLF = "\r\n"
    if fields != None:
        for (key, value) in fields:
            body.fromstring("--" + BOUNDARY + CRLF)
            body.fromstring(('Content-Disposition: form-data; name="%s"' % key) + CRLF)
            body.fromstring("Content-Type: text/plain; charset=utf-8" + CRLF)
            body.fromstring(CRLF)
            body.fromstring(value.encode("utf-8"))
            body.fromstring(CRLF)

    if files != None:
        for (key, filename, value, content_type) in files:
            body.fromstring("--" + BOUNDARY + CRLF)
            body.fromstring(('Content-Disposition: form-data; name="%s"; filename="%s"' % (key, filename)) + CRLF)
            body.fromstring(("Content-Type: %s" % content_type) + CRLF)
            body.fromstring(CRLF)
            body.fromstring(value)
            body.fromstring(CRLF)

    body.fromstring("--" + BOUNDARY + "--" + CRLF)
    body.fromstring(CRLF)

    return "multipart/form-data; boundary=%s" % BOUNDARY, body

# Everything below here is not considered public at all

# JS object that handles "confirm" method
class JSConfirmMethod( Javascript.JSObject ):

    def __init__( self, window_instance ):
        Javascript.JSObject.__init__( self )
        assert isinstance( window_instance, VirtualWindow )
        self.window_instance = window_instance

    def call( self, argset, context ):
        # Check to be sure we have one string argument
        if len(argset) != 1:
            raise Exception("confirm method requires one string argument")
        # Evaluate to string
        message = argset[0].str_value( )
        print >>sys.stderr, "CONFIRM: "+message
        # Now, decide whether we return true or false.
        return Javascript.JSBoolean( self.window_instance.get_answer( message, True ) )

# JS object that handles "alert" method
class JSAlertMethod( Javascript.JSObject ):

    def __init__( self, window_instance ):
        Javascript.JSObject.__init__( self )
        assert isinstance( window_instance, VirtualWindow )
        self.window_instance = window_instance

    def call( self, argset, context ):
        # Check to be sure we have one string argument
        if len(argset) != 1:
            raise Exception("alert method requires one string argument")
        # Evaluate just to be sure there's no error
        message = argset[0].str_value( )
        print >>sys.stderr, "ALERT: "+message
        # Always click "OK"
        return Javascript.JSBoolean( True )

# JS object that handles "eval" method
class JSEvalMethod( Javascript.JSObject ):

    def __init__( self, window_instance ):
        Javascript.JSObject.__init__( self )
        assert isinstance( window_instance, VirtualWindow )
        self.window_instance = window_instance

    def call( self, argset, context ):
        # Check to be sure we have one string argument
        if len(argset) != 1:
            raise Exception("eval method requires one string argument")
        # Evaluate to string
        message = argset[0].str_value( )
        # Parse this as javascript
        tokenstream = Javascript.JSTokenStream( message )
        return tokenstream.evaluate_expr( context, "Eval" )

# JS object that handles "parseInt" method
class JSParseIntMethod( Javascript.JSObject ):

    def __init__( self, window_instance ):
        Javascript.JSObject.__init__( self )
        assert isinstance( window_instance, VirtualWindow )
        self.window_instance = window_instance

    def call( self, argset, context ):
        # Check to be sure we have one string argument
        if len(argset) != 1:
            raise Exception("parseInt method requires one string argument")
        # Evaluate to int
        intvalue = argset[0].int_value( )
        return Javascript.JSNumber( intvalue )

# JS object that handles "submit" method
class JSSubmitMethod( Javascript.JSObject ):

    def __init__ ( self, form_instance ):
        Javascript.JSObject.__init__( self )
        assert isinstance( form_instance, VirtualForm )
        self.form_instance = form_instance

    def call( self, argset, context ):
        # Check to be sure we have no arguments
        if len(argset) != 0:
            raise Exception("submit method has no arguments")
        self.form_instance.submit( )
        return Javascript.JSBoolean( True )

# Class representing focus method
class JSFocusMethod( Javascript.JSObject ):
    def __init__ ( self, owner ):
        Javascript.JSObject.__init__( self )
        assert isinstance( owner, VirtualFormDataElement )
        self.owner = owner

    def call( self, argset, context ):
        print >>sys.stderr, "FOCUS: On field '%s'" % self.owner.get_name( )
        return Javascript.JSBoolean( True )

# JS object representing a window in Javascript
class JSWindowObject( Javascript.JSObject ):

    def __init__( self, window_object ):
        Javascript.JSObject.__init__( self )
        assert isinstance( window_object, VirtualWindow )
        self.window_object = window_object

    def get_value( self, member_name ):
        # Check to see if there is a js object saved with the
        # specified name.
        return Javascript.JSBoolean( self.window_object.check_exists( member_name ) )

    def set_value( self, member_name, value ):
        raise Exception("Cannot set properties of window object")


# JS object representing a document in Javascript
class JSDocObject( Javascript.JSObject ):

    def __init__( self ):
        Javascript.JSObject.__init__( self )
        self.forms = { }

    def add_form( self, form_name, jsobject ):
        self.forms[ form_name ] = jsobject

    def get_value( self, member_name ):
        try:
            return self.forms[ member_name ]
        except:
            return Javascript.JSObject.get_value( self, member_name )

    def set_value( self, member_name, value ):
        if member_name != "onkeypress":
            raise Exception("Cannot set properties of document object")

# Class representing a form in Javascript
class JSFormObject( Javascript.JSObject ):

    def __init__( self, virtual_form_object ):
        Javascript.JSObject.__init__( self )
        assert isinstance( virtual_form_object, VirtualForm )
        self.virtual_form_object = virtual_form_object

    def get_value( self, member_name ):
        # Properties and methods available for all forms include the elements of the
        # forms (as objects), as well as methods.
        # The only method available right now is submit()
        if member_name == "submit":
            return JSSubmitMethod( self.virtual_form_object )
        elif member_name == "action":
            return Javascript.JSString( self.virtual_form_object.get_action_url() )

        # Find the element on the form of this name
        try:
            form_element = self.virtual_form_object.find_element_by_dataname( member_name )
        except:
            Javascript.JSObject.get_value( self, member_name )
        return JSElementObject( form_element )

    def set_value( self, member_name, value ):
        if member_name == "action":
            self.virtual_form_object.set_action_url( value.str_value() )
            return

        # Can't set anything here
        raise Exception("Cannot set properties of form object")

# Class representing an element in Javascript
class JSElementObject( Javascript.JSObject ):

    def __init__( self, element_object ):
        Javascript.JSObject.__init__( self )
        assert isinstance( element_object, VirtualFormDataElement )
        self.element_object = element_object

    def get_type( self, member_name ):
        # We need to return the proper types of the javascript object
        # properties
        value = self.element_object.get_property_type( member_name )
        return value
        
    def get_value( self, member_name ):
        # The object itself knows what its javascript properties are, so call the right
        # method inside.  All properties are currently strings.
        value = self.element_object.get_property( member_name )
        if isinstance( value, Javascript.JSObject ) == False:
            raise Exception("Property returned from element %s member %s is not a JSObject, it is: %s" % (self.element_object.get_name(), member_name, str(value)))
        return value

    def set_value( self, member_name, value ):
        # The object itself knows what its javascript properties are.  Pass them
        # as strings, though.
        self.element_object.set_property( member_name, value )

    def type_value( self ):
        return "formelement"

    def bool_value( self ):
        # Return true because the object clearly exists
        return True

class JSOptionClassInstance (Javascript.JSObject ):

    def __init__( self, text, value, selected=False ):
        Javascript.JSObject.__init__( self )
        self.text = text
        self.value = value
        self.selected = Javascript.JSBoolean(selected)

    def get_value( self, member_name ):
        if member_name == "text":
            return self.text
        elif member_name == "value":
            return self.value
        elif member_name == "selected":
            return self.selected
        return Javascript.JSObject.get_value( self, member_name )

    def set_value( self, member_name, value ):
        if member_name == "text":
            self.text = value
        elif member_name == "value":
            self.value = value
        elif member_name == "selected":
            self.selected = value.bool_value()
        else:
            Javascript.JSObject.set_value( self, member_name, value )

class JSOptionClassDef( Javascript.JSObject ):

    def __init__( self ):
        Javascript.JSObject.__init__( self )

    def construct( self, argset, context ):
        # We only accept the 2-argument form: (text, value)
        if len(argset) != 2:
            raise Exception("Expected two arguments to Option constructor, saw %d" % len(argset))
        # Save the two objects
        return JSOptionClassInstance(argset[0],argset[1])

# This class handles our variety of HTML parsing.
# We are really interested only in A HREFs, FORM, INPUT, and SCRIPT tags at this time.
class VirtualActionParser( HTMLParser.HTMLParser ):

    def __init__( self, window_instance ):
        HTMLParser.HTMLParser.__init__( self )
        self.window_instance = window_instance
        # We do not allow nested forms right now
        self.current_form_instance = None
        self.current_selectbox = None
        self.current_radio = None
        self.current_checkbox = None
        self.current_textarea = None
        self.current_link = None
        self.current_option_value = None
        self.current_option_value_selected = None
        self.current_data = None
        self.current_comment = None
        self.current_select_active = False
        self.current_input_active = False
        self.current_option_active = False
        self.current_anchor_active = False
        self.current_form_active = False
        self.current_script_active = False
        self.current_textarea_active = False
        self.tagstack = [ ]

    def handle_starttag( self, tag, attributes ):
        self.tagstack.append( tag )
        if tag == "a":
            self.start_a( attributes )
        elif tag == "input":
            self.start_input( attributes )
        elif tag == "option":
            self.start_option( attributes )
        elif tag == "form":
            self.start_form( attributes )
        elif tag == "select":
            self.start_select( attributes )
        elif tag == "script":
            self.start_script( attributes )
        elif tag == "textarea":
            self.start_textarea( attributes )

    def handle_endtag( self, tag ):
        lastguy = self.tagstack[ len(self.tagstack) - 1 ]
        if lastguy != tag:
            raise Exception("Unmatched tag: %s. Found instead: %s" % (lastguy, tag) )
        del self.tagstack[ len(self.tagstack) - 1 ]
        if tag == "a":
            self.end_a( )
        elif tag == "input":
            self.end_input( )
        elif tag == "option":
            self.end_option( )
        elif tag == "form":
            self.end_form( )
        elif tag == "select":
            self.end_select( )
        elif tag == "script":
            self.end_script( )
        elif tag == "textarea":
            self.end_textarea( )

    def start_a( self, attributes ):
        # We only care about HREF=
        if self.current_link != None:
            raise Exception("Anchor within anchor: not allowed")
        self.current_anchor_active = True
        dict = make_dictionary( attributes )
        try:
            href = dict[ "href" ]
            # IE6, 7, and Firefox 3 all do a URL Decode at this point...
            href = urllib.unquote(href)
            alt = dict[ "alt" ]
            onclick = None
            try:
                onclick = dict[ "onclick" ]
            except:
                if href.lower().startswith("javascript:"):
                    onclick = href
                    href = None
            self.current_data = ""
            self.current_link = VirtualLink( self.window_instance, alt, href, onclick )
        except:
            pass

    def end_a( self ):
        if self.current_anchor_active == False:
            raise Exception("Unmatched anchor tag; end without start")
        self.current_anchor_active == False
        if self.current_link != None:
            # Use the saved data as link body
            self.current_link.set_bodytext( self.current_data )
            # Save the link
            self.window_instance.add_link( self.current_link )
            self.current_link = None
        self.current_data = None

    def start_form( self, attributes ):
        if self.current_form_instance != None:
            raise Exception("Nested forms not allowed")
        dict = make_dictionary( attributes )
        self.current_form_active = True
        try:
            action = dict[ "action" ]
            name = dict[ "name" ]
            try:
                method = dict[ "method" ]
                method = method.upper( )
            except:
                method = "GET"
            try:
                type_of_form = dict[ "enctype" ]
                if type_of_form == "multipart/form-data":
                    multipart = True
                else:
                    multipart = False
            except:
                multipart = False
            if method == "POST" and multipart == True:
                method = "MULTIPART"
            print >>sys.stderr, "Form of type %s detected" % method
            self.current_form_instance = VirtualForm( self.window_instance, name, action, method )
            self.window_instance.add_form( self.current_form_instance )
        except:
            pass

    def end_form( self ):
        if self.current_form_active == False:
            raise Exception("Error, form end without form start")
        self.current_form_active = False
        if self.current_form_instance != None:
            self.current_form_instance = None

    def start_select( self, attributes ):
        if self.current_select_active:
            raise Exception("Error, can't nest selects")
        if self.current_form_instance == None:
            raise Exception("Select not legal outside form")
        self.current_select_active = True
        dict = make_dictionary( attributes )
        try:
            name = dict[ "name" ]
        except:
            raise Exception("Illegal select tag [no name attribute]")
        try:
            multiple = dict[ "multiple" ]
        except:
            multiple = "false"
        self.current_selectbox = VirtualSelectbox( self.current_form_instance, name, multiple.lower() == "true" )

    def end_select( self ):
        if self.current_select_active == False:
            raise Exception("Error, no starting select tag")
        self.current_select_active = False
        if self.current_selectbox != None:
            self.current_form_instance.add_element( self.current_selectbox )
            self.current_selectbox = None


    def start_textarea( self, attributes ):
        if self.current_textarea_active:
            raise Exception("Error, can't nest textareas")
        if self.current_form_instance == None:
            raise Exception("Textarea not legal outside form")
        self.current_textarea_active = True
        dict = make_dictionary( attributes )
        try:
            name = dict[ "name" ]
        except:
            raise Exception("Error, textarea has no name attribute")
        self.current_textarea = VirtualTextarea( self.current_form_instance, name )
        self.current_data = ""

    def end_textarea( self ):
        if self.current_textarea_active == False:
            raise Exception("Error, no starting textarea tag")
        self.current_textarea_active = False
        if self.current_textarea != None:
            self.current_textarea.set_value( self.current_data )
            self.current_form_instance.add_element( self.current_textarea )
            self.current_textarea = None
        self.current_data = None

    def start_input( self, attributes ):
        if self.current_input_active:
            raise Exception("Error, can't nest inputs")
        self.current_input_active = True
        if self.current_form_instance != None:
            dict = make_dictionary( attributes )
            try:
                type = dict[ "type" ].lower()
            except:
                raise Exception("Illegal input tag [no type attribute]")

            if type == "button":
                try:
                    value = dict[ "value" ]
                except:
                    raise Exception("Input type button must have a value")
                try:
                    onclick = dict[ "onclick" ]
                except:
                    raise Exception("Input type button must have an onclick script")
                try:
                    alt = dict[ "alt" ]
                    self.window_instance.add_button( VirtualBasicbutton( self.current_form_instance, alt, value, onclick ) )
                except:
                    pass

            elif type == "password":
                try:
                    name = dict[ "name" ]
                except:
                    raise Exception("Input type password must have name attribute")
                try:
                    value = dict[ "value" ]
                except:
                    raise Exception("Input type password must have value attribute")
                inst = VirtualTextarea( self.current_form_instance, name )
                inst.set_value( value )
                self.current_form_instance.add_element( inst )

            elif type == "text":
                try:
                    name = dict[ "name" ]
                except:
                    raise Exception("Input type text must have name attribute")
                try:
                    value = dict[ "value" ]
                except:
                    raise Exception("Input type text must have value attribute")
                inst = VirtualTextarea( self.current_form_instance, name )
                inst.set_value( value )
                self.current_form_instance.add_element( inst )

            elif type == "hidden":
                try:
                    name = dict[ "name" ]
                except:
                    raise Exception("Input type hidden must have name attribute")
                try:
                    value = dict[ "value" ]
                except:
                    raise Exception("Input type hidden must have value attribute")
                # Hiddens are special; they act like they can contain multiple values for a given name.
                try:
                    existing_hidden = self.current_form_instance.find_element_by_dataname(name)
                    existing_hidden.add_value(value)
                except:
                    self.current_form_instance.add_element( VirtualHiddenField( self.current_form_instance, name, value ) )

            elif type == "submit":
                try:
                    name = dict[ "name" ]
                except:
                    raise Exception("Input type submit must have name attribute")
                try:
                    value = dict[ "value" ]
                except:
                    value = "Submit"
                try:
                    onclick = dict[ "onclick" ]
                except:
                    onclick = None
                try:
                    alt = dict[ "alt" ]
                    formobject = VirtualSubmitbutton( self.current_form_instance, name, value, alt, onclick )
                    self.current_form_instance.add_element( formobject )
                    self.window_instance.add_button( VirtualSubmitbuttonWrapper( formobject ) )
                except:
                    pass

            elif type == "radio":
                try:
                    name = dict[ "name" ]
                except:
                    raise Exception("Input type radio must have name attribute")
                try:
                    value = dict[ "value" ]
                except:
                    raise Exception("Input type radio must have value attribute")
                try:
                    selected = dict[ "checked" ]
                except:
                    selected = "false"

                self.current_radio = VirtualRadiobutton( self.current_form_instance, name, value, selected == "true" or selected == "" or selected == "yes" )
                self.current_data = ""

            elif type == "checkbox":
                try:
                    name = dict[ "name" ]
                except:
                    raise Exception("Input type checkbox must have name attribute")
                try:
                    value = dict[ "value" ]
                except:
                    raise Exception("Input type checkbox must have value attribute")
                try:
                    selected = dict[ "checked" ]
                except:
                    selected = "false"

                self.current_checkbox = VirtualCheckbox( self.current_form_instance, name, value, selected == "true" )
                self.current_data = ""

            elif type == "file":
                try:
                    name = dict[ "name" ]
                except:
                    raise Exception("Input type file must have name attribute")
                self.current_form_instance.add_element( VirtualFileBrowser( self.current_form_instance, name ) )

            else:
                raise Exception("Unsupported input tag type: %s" % type)

    def end_input( self ):
        if self.current_input_active == False:
            raise Exception("Error, no starting input tag")
        self.current_input_active = False
        if self.current_form_instance:
            # Action depends on what's set
            if self.current_checkbox != None:
                self.current_checkbox.set_bodytext( self.current_data )
                self.current_form_instance.add_element( self.current_checkbox )
                self.current_checkbox = None
            elif self.current_radio != None:
                self.current_radio.set_bodytext( self.current_data )
                self.current_form_instance.add_element( self.current_radio )
                self.current_radio = None

            self.current_data = None

    def start_option( self, attributes ):
        if self.current_option_active:
            raise Exception("Error, can't nest options")
        if self.current_form_instance == None:
            raise Exception("Option not legal outside form")
        if self.current_select_active == False:
            raise Exception("Option not legal outside select")
        self.current_option_active = True
        dict = make_dictionary( attributes )
        try:
            self.current_option_value = dict[ "value" ]
        except:
            raise Exception("All options must have explicit values")
        try:
            self.current_option_value_selected = dict[ "selected" ]
        except:
            self.current_option_value_selected = None

        self.current_data = ""

    def end_option( self ):
        if self.current_option_active == False:
            raise Exception("Error, no starting option tag")
        self.current_option_active = False
        if self.current_option_value != None:
            self.current_selectbox.add_selection( self.current_option_value, self.current_data, self.current_option_value_selected != None )
            self.current_option_value = None
            self.current_option_value_selected = None
        self.current_data = None

    def start_script( self, attributes ):
        if self.current_script_active:
            raise Exception("Nested scripts not legal")
        self.current_script_active = True
        dict = make_dictionary( attributes )
        try:
            type = dict[ "type" ]
            if type == "text/javascript":
                self.current_data = ""
                self.current_comment = ""
        except:
            pass

    def end_script( self ):
        if self.current_script_active == False:
            raise Exception("Error, script end without script start")
        self.current_script_active = False
        if self.current_comment != None:
            javascript_text = self.current_data
            self.current_data = None
            # Feed the javascript to the JS engine via the window
            self.window_instance.accept_javascript( javascript_text )
        elif self.current_data != None:
            javascript_text = self.current_comment
            self.current_comment = None
            self.window_instance.accept_javascript( javascript_text )


    def handle_data( self, data ):
        if self.current_data != None:
            self.current_data = self.current_data + data

    # This is no longer needed; we don't grab stuff from comments, just from <!-- blocks
    # def handle_comment( self, data ):
    #   if self.current_comment != None:
    #       self.current_comment = self.current_comment + data

# Convert a parameter tuple list into a dictionary.
# The urllib2.urlencode( ) method probably does this, but that isn't certain, so I'll
# not remove this code until I am sure.
def make_dictionary( parameters ):
    post_parameters = { }
    # If a particular parameter appears more than once, make it be semicolon - separated
    for parameter, value in parameters:
        value = decode_attribute(value)
        try:
            current_value = post_parameters[ parameter ]
            post_parameters[ parameter ] = current_value + ";" + value
        except:
            post_parameters[ parameter ] = value
    return post_parameters

# Decode HTML-encoded attribute value
def decode_attribute( value ):
    output_value = ""
    index = 0
    while True:
        new_index = value.find("&",index)
        if new_index == -1:
            return output_value + value[index:len(value)]
        output_value = output_value + value[index:new_index]
        end_value = value.find(";",new_index)
        if end_value == -1:
            index = new_index + 1
            output_value = output_value + "&"
            continue
        char_description = value[new_index+1:end_value]
        index = end_value + 1
        if char_description.lower() == "amp":
            output_value = output_value + "&"
        elif char_description.startswith("#"):
            value_to_convert = char_description[1:len(char_description)]
            output_value = output_value + chr(int(value_to_convert))

if __name__ == "__main__":
    vb = VirtualBrowser( )
    vb.load_main_window( "http://mcweb.metacarta.com" )
