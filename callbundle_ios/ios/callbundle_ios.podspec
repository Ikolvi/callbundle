#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint callbundle_ios.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'callbundle_ios'
  s.version          = '1.0.4'
  s.summary          = 'iOS implementation of the CallBundle plugin using CallKit.'
  s.description      = <<-DESC
iOS implementation of CallBundle providing native incoming/outgoing call UI
via CallKit. Handles PushKit, audio session management, and missed call
notifications inside the plugin — no AppDelegate code required.
                       DESC
  s.homepage         = 'https://ikolvi.com'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Ikolvi' => 'info@ikolvi.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '13.0'
  s.frameworks = 'CallKit', 'PushKit', 'AVFoundation', 'UserNotifications'

  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # s.resource_bundles = {'callbundle_ios_privacy' => ['Resources/PrivacyInfo.xcprivacy']}
end
