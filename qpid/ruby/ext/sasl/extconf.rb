require 'mkmf'

extension_name = 'sasl'

def create_dummy_makefile
  File.open("Makefile", 'w') do |f|
    f.puts "all:"
    f.puts "install:"
  end
end

if have_library("c", "main") and have_library("sasl2")
  puts "Building sasl.so for authentication support."
  create_makefile(extension_name)
else
  puts "Package cyrus-sasl-devel not found, SASL support disabled"
  puts "If building on windows and it fails with a message about not finding make try running this command first"
  puts "\t set make=echo"
  create_dummy_makefile
end

