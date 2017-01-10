#!/usr/bin/python
#
# Copyright 2010 Google Inc. All Rights Reserved.

"""Code generator for MetricX, EventMetricX, VirtualMetricX classes.

These classes contain a certain amount of redundant code, in order to
provide type-safe access to metric fields. As java doesn't allow the
same 'Unit' trick that we use in the C++ Streamz runtime library, we
generate the different dimensionality options instead.

When invoked with the --check_consistency option, the files are
not actually updated, but checked to see whether they is up-to-date.
This can be used in both pre-submit checks and cheap build-time checks
to ensure consistency of the derived file.

We check in the generated source instead of having a genrule, to make
it easier for users to look at the source.
"""

__author__ = 'ecurran@google.com (Eoin Curran)'

import os
import re
import sys

NUMBERS = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven',
           'eight', 'nine', 'ten']
ORDERING = ['0th', 'first', 'second', 'third', 'fourth', 'fifth', 'sixth',
            'seventh', 'eighth', 'ninth', 'tenth']
FACTORY_METHOD_CLASSES = ['MetricFactory']
DEPRECATED_ENTITIES_MAX_DIMENSIONALITY = 7


def _WriteFile(this_dir, template_file, dest_file, template_vars):
  """Expands a template and writes the result to a file.

  If --check_consistency is set, checks the contents of the file instead
  of writing it.

  Args:
    this_dir: The streamz package directory.
    template_file: The name of the template file, relative to this_dir.
    dest_file: The destination file, relative to this_dir.
    template_vars: Dictionary of variables to substitute in the template.
  """
  print >>sys.stderr, ("Writing %s to %s" % (template_file, dest_file))
  template = open(os.path.join(this_dir, template_file), 'r').read()
  file_name = os.path.join(this_dir, dest_file)
  # if not re.search(FLAGS.output_filename_regexp, dest_file):
  #  print ("(skipping %s which doesn't partial-match %s)" %
  #         (dest_file, FLAGS.output_filename_regexp))
  #  return
  new_contents = template % template_vars

  # if we haven't yet generated any of the files, then this will create the
  # file.  Otherwise the test for "Readonly" failes without reason.
  if not os.access(file_name, os.F_OK):
    tmp = open(file_name, 'w+')
    tmp.close()

  is_readonly = not os.access(file_name, os.W_OK)

  if is_readonly:
    current_contents = open(file_name, 'r').read()
    if new_contents != current_contents:
      print >>sys.stderr, ('Check failed: %s cannot be modified. '
                           'Run g4 edit %s ; generate.py or diff %s %s.new' %
                           (file_name, file_name, file_name, file_name))
      open(file_name + '.new', 'w').write(new_contents)
      sys.exit(1)
  else:
    open(file_name, 'w').write(new_contents)


def _GenerateFiles(this_dir, max_metric_dimension):
  """Generates all metric code up to max_metric_dimension.

  This includes MetricFactory factory methods corresponding to each dimension.

  Args:
    this_dir: The streamz package directory.
  """
  factory_methods_templates = {}
  factory_methods = {}
  for className in FACTORY_METHOD_CLASSES:
    factory_methods_templates[className] = open(
        os.path.join(this_dir, '%s.factoryMethods.java.template' % className),
        'r').read()
    factory_methods[className] = ''

  # tester_factory_methods_templates = open(os.path.join(this_dir,
  #                                                     'testing/StreamzTester.factoryMethods.java.template'),
  #                                        'r').read()
  # tester_factory_methods = ''

  for dimensionality in range(1, max_metric_dimension + 1):
    type_javadoc = ''
    types_erased = []
    types = []
    params = []
    values = []
    field_params = []
    field_params_javadoc = ''
    new_field_params_javadoc = ''
    fields_created = []
    new_field_params = []
    fields = []
    field_values = []
    field_names_javadoc = ''
    field_names_params = []
    field_names = []

    for i in range(1, dimensionality + 1):
      type_javadoc += (
        ' * @param <F%d> The type of the %s metric field.\n' %
        (i, ORDERING[i]))

      types.append('F%d' % (i))
      types_erased.append('Object')
      params.append('F%d field%d' % (i, i))
      values.append('field%d' % (i))
      field_params.append(
          'Class<F%d> field%dClass, String field%dName' % (i, i, i))
      new_field_params.append('Field<F%d> field%d' % (i, i))
      field_values.append('field%dClass, field%dName' % (i, i))
      field_params_javadoc += """
   * @param field%dClass The class of field%d: String, Boolean or Integer
   * @param field%dName The name of field%d""" % (i, i, i, i)
      new_field_params_javadoc += """
   * @param field%d The %s field""" % (i, ORDERING[i])
      field_names_javadoc += """
   * @param field%dName The name of field%d""" % (i, i)
      field_names_params.append('String field%dName' % (i))
      field_names.append('field%dName' % (i))
      fields_created.append(
          'new Field<F%d>(field%dName, field%dClass, null)' %
          (i, i, i))
      fields.append('field%d' % (i))
    template_vars = {
      'generator': 'generate.py',
      'dimensionality': dimensionality,
      'number': NUMBERS[dimensionality],
      'types': ', '.join(types),
      'types_erased': ', '.join(types_erased),
      'params': ',\n      '.join(params),
      'values': ', '.join(values),
      'field_params': ',\n      '.join(field_params),
      'field_values': ',\n        '.join(field_values),
      'fields_created': ',\n            '.join(fields_created),
      'new_field_params': ',\n      '.join(new_field_params),
      'fields': ',\n            '.join(fields),
      'field_params_javadoc': field_params_javadoc,
      'new_field_params_javadoc': new_field_params_javadoc,
      'field_names_params': ',\n      '.join(field_names_params),
      'field_names': ','.join(field_names),
      'field_names_javadoc': field_names_javadoc,
      'type_javadoc': type_javadoc
    }

    _WriteFile(this_dir, 'Metric.java.template',
               'Metric%d.java' % dimensionality, template_vars)
    _WriteFile(this_dir, 'CallbackMetric.java.template',
               'CallbackMetric%d.java' % dimensionality, template_vars)
    _WriteFile(this_dir, 'Counter.java.template',
               'Counter%d.java' % dimensionality, template_vars)
    # _WriteFile(this_dir, 'testing/MetricReference.java.template',
    #            'testing/MetricReference%d.java' % dimensionality, template_vars)
    _WriteFile(this_dir, 'EventMetric.java.template',
               'EventMetric%d.java' % dimensionality, template_vars)
    if dimensionality <= DEPRECATED_ENTITIES_MAX_DIMENSIONALITY:
      _WriteFile(this_dir, 'VirtualMetric.java.template',
                 'VirtualMetric%d.java' % dimensionality, template_vars)

    for className in FACTORY_METHOD_CLASSES:
      factory_methods_templates[className] = open(
          os.path.join(this_dir, '%s.factoryMethods.java.template' % className),
          'r').read()
      # if dimensionality <= DEPRECATED_ENTITIES_MAX_DIMENSIONALITY:
      #  factory_methods_templates[className] += (
      #    open(os.path.join(
      #        this_dir,
      #        '%s.deprecatedFactoryMethods.java.template' % className), 'r'
      #    ).read())
      factory_methods[className] += (
        factory_methods_templates[className] % template_vars)

    # tester_factory_methods += tester_factory_methods_templates % template_vars

  template_vars = {
    'generator': 'generate.py'
  }
  for className in FACTORY_METHOD_CLASSES:
    template_vars['factory_methods_' + className] = factory_methods[className]

  _WriteFile(this_dir, 'MetricFactory.java.template',
             'MetricFactory.java', template_vars)

  # template_vars = {
  #  'generator': 'generate.py',
  #  'factory_methods': tester_factory_methods
  # }
  # _WriteFile(this_dir, 'testing/StreamzTester.java.template',
  #           'testing/StreamzTester.java', template_vars)

def main(argv):
  this_dir = os.path.dirname(os.path.realpath(argv[0]))
  _GenerateFiles(this_dir, 10)

if __name__ == '__main__':
  main(sys.argv)
