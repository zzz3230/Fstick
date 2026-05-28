path = r'C:\F-stick\fstickfrontend\element-web\apps\web\src\fstick\dsl\libCode.ts'
with open(path, 'rb') as f:
    raw = f.read()

if raw[:2] == b'\xff\xfe':
    content = raw[2:].decode('utf-16-le')
    print('Detected UTF-16 LE, converting to UTF-8')
elif raw[:2] == b'\xfe\xff':
    content = raw[2:].decode('utf-16-be')
    print('Detected UTF-16 BE, converting to UTF-8')
elif raw[:3] == b'\xef\xbb\xbf':
    content = raw[3:].decode('utf-8')
    print('Detected UTF-8 BOM, removing BOM')
else:
    content = raw.decode('utf-8')
    print('Already UTF-8 without BOM, nothing to do')

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print('Done. File rewritten as UTF-8 without BOM.')


