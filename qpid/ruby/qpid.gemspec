# Generated by jeweler
# DO NOT EDIT THIS FILE DIRECTLY
# Instead, edit Jeweler::Tasks in Rakefile, and run the gemspec command
# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = %q{qpid}
  s.version = "0.10.4"

  s.required_rubygems_version = Gem::Requirement.new(">= 0") if s.respond_to? :required_rubygems_version=
  s.authors = [""]
  s.date = %q{2010-03-18}
  s.description = %q{Ruby client for Qpid}
  s.email = %q{qpid-dev@incubator.apache.org}
  s.extensions = ["ext/sasl/extconf.rb"]
  s.files = [
    "LICENSE.txt",
     "NOTICE.txt",
     "RELEASE_NOTES",
     "Rakefile",
     "examples/hello-world.rb",
     "examples/qmf-libvirt.rb",
     "ext/sasl/extconf.rb",
     "ext/sasl/sasl.c",
     "lib/qpid.rb",
     "lib/qpid/assembler.rb",
     "lib/qpid/client.rb",
     "lib/qpid/codec.rb",
     "lib/qpid/codec08.rb",
     "lib/qpid/config.rb",
     "lib/qpid/connection.rb",
     "lib/qpid/connection08.rb",
     "lib/qpid/datatypes.rb",
     "lib/qpid/delegates.rb",
     "lib/qpid/fields.rb",
     "lib/qpid/framer.rb",
     "lib/qpid/invoker.rb",
     "lib/qpid/packer.rb",
     "lib/qpid/peer.rb",
     "lib/qpid/qmf.rb",
     "lib/qpid/queue.rb",
     "lib/qpid/session.rb",
     "lib/qpid/spec.rb",
     "lib/qpid/spec010.rb",
     "lib/qpid/spec08.rb",
     "lib/qpid/test.rb",
     "lib/qpid/traverse.rb",
     "lib/qpid/util.rb",
     "specs/amqp-dtx-preview.0-9.xml",
     "specs/amqp-errata.0-9.xml",
     "specs/amqp-nogen.0-9.xml",
     "specs/amqp.0-10-preview.xml",
     "specs/amqp.0-10-qpid-errata.xml",
     "specs/amqp.0-10.xml",
     "specs/amqp.0-8.xml",
     "specs/amqp.0-9.xml",
     "specs/amqp0-9-1.stripped.xml",
     "specs/cluster.0-8.xml",
     "specs/management-schema.xml",
     "tests/assembler.rb",
     "tests/codec010.rb",
     "tests/connection.rb",
     "tests/datatypes.rb",
     "tests/framer.rb",
     "tests/qmf.rb",
     "tests/queue.rb",
     "tests/spec010.rb",
     "tests/util.rb"
  ]
  s.homepage = %q{http://cwiki.apache.org/qpid/}
  s.rdoc_options = ["--charset=UTF-8"]
  s.require_paths = ["lib"]
  s.required_ruby_version = Gem::Requirement.new(">= 1.8.1")
  s.rubygems_version = %q{1.3.5}
  s.summary = %q{Ruby client for Qpid}

  if s.respond_to? :specification_version then
    current_version = Gem::Specification::CURRENT_SPECIFICATION_VERSION
    s.specification_version = 3

    if Gem::Version.new(Gem::RubyGemsVersion) >= Gem::Version.new('1.2.0') then
    else
    end
  else
  end
end

