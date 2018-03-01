import sys
import os
import shutil
import subprocess

def svn_command(command_array):
    """ Invoke svn command """
    popen = subprocess.Popen(["svn"] + command_array,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out,err = popen.communicate()
    rcode = popen.returncode
    if rcode != 0:
        raise Exception("svn invocation errored with code %d: %s" % (rcode,err))
    return out
    
def site_merge(source_dir, target_dir):
    """ Merge the source dir into the target dir, in a way that captures
        differences that SVN can pick up.
    """
    # First, handle deletes.  Go through the target tree and find everything that doesn't
    # exist in the source...
    print >> sys.stderr, "Removing obsolete directories and files..."
    deleted_dirs = { }
    for root, dirs, files in os.walk(target_dir):
        if root.find(".svn") == -1:
            # Remove target_dir prefix from root
            relative_root = root[len(target_dir):]
            if len(relative_root) > 0 and (relative_root[0] == "/" or relative_root[0] == "\\"):
                relative_root = relative_root[1:]
            if not deleted_dirs.has_key(relative_root):
                # Remove any dirs that have gone away
                for dir in dirs:
                    if dir != ".svn":
                        relative_dirname = os.path.join(relative_root, dir)
                        target_dirname = os.path.join(root, dir)
                        source_dirname = os.path.join(source_dir, relative_dirname)
                        if os.path.exists(target_dirname):
                            if not os.path.exists(source_dirname):
                                print >> sys.stderr, "Deleting directory %s" % relative_dirname
                                svn_command(["remove",target_dirname])
                                deleted_dirs[relative_dirname] = True
                # Process files now.
                for file in files:
                    relative_filename = os.path.join(relative_root, file)
                    target_filename = os.path.join(root, file)
                    source_filename = os.path.join(source_dir, relative_filename)
                    if os.path.exists(target_filename):
                        if not os.path.exists(source_filename):
                            print >> sys.stderr, "Deleting file %s" % relative_filename
                            svn_command(["remove",target_filename])

    # Now, we do the same thing for the source tree.  We will add the missing directories and
    # copy and add the files.
    print >> sys.stderr, "Adding and updating new directories and files..."
    for root, dirs, files in os.walk(source_dir):
        relative_root = root[len(source_dir):]
        if len(relative_root) > 0 and (relative_root[0] == "/" or relative_root[0] == "\\"):
            relative_root = relative_root[1:]
        for dir in dirs:
            relative_dirname = os.path.join(relative_root, dir)
            target_dirname = os.path.join(target_dir, relative_dirname)
            source_dirname = os.path.join(root, dir)
            #svn_result = svn_command(["status",target_dirname])
            #print >> sys.stderr, "Target: %s, svn result %s" % (target_dirname,svn_result)
            #if len(svn_result) == 0 or svn_result[0] == "?":
            succeeded = False
            try:
                svn_command(["mkdir",target_dirname])
                succeeded = True
            except:
                pass
            if succeeded:
                print >> sys.stderr, "Adding directory %s" % relative_dirname
        for file in files:
            relative_filename = os.path.join(relative_root, file)
            target_filename = os.path.join(target_dir, relative_filename)
            source_filename = os.path.join(root, file)
            # Copy source to target
            shutil.copyfile(source_filename, target_filename)
            svn_result = svn_command(["status",target_filename])
            if len(svn_result) > 0 and svn_result[0] == "?":
                print >> sys.stderr, "Adding file %s" % relative_filename
                svn_command(["add",target_filename])
    
if __name__ == '__main__':
    if len(sys.argv) != 2 and len(sys.argv) != 3:
        print >> sys.stderr, "Usage: %s <src_dir> [<target_dir>]" % sys.argv[0]
        sys.exit(1)
    
    source_dir = sys.argv[1]
    if len(sys.argv) > 2:
        target_dir = sys.argv[2]
    else:
        target_dir = "publish"
    
    svn_command([ "update", target_dir])

    site_merge(source_dir, target_dir)
    
    print >> sys.stderr, "Committing changes..."
    svn_command([ "-m", "Update ManifoldCF site", "commit", target_dir])
    
    print >> sys.stderr, "Site updated!"
    